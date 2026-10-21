//! SVBK's synchronous 60 Hz ECL companion. Geometry/combat/input/UI remain in Java.
//! The ECL, ex-instruction, ANM and bullet interpreters are vendored under MIT; see UPSTREAM.
extern crate self as th06_formats;
mod ecl; mod anm0; mod anm_vm; mod ecl_vm; mod physics;
use std::collections::{HashMap, HashSet};
use std::io::{self, Read, Write, BufReader, BufWriter};
use std::path::{Path, PathBuf};
use ecl::Ecl;
use ecl_vm::*;
use anm_vm::AnmRunner;

struct Host {
    ecl: Ecl, world: World, enemies: Vec<Enemy>, generations: Vec<u32>,
    anims: Vec<Option<AnmRunner>>, sub_anims: Vec<[Option<AnmRunner>; 8]>,
    scripts: HashMap<i32, Vec<anm0::Instr>>, sizes: HashMap<u32, (f32,f32)>,
    frame: u32, main_started: bool, intro_interrupted: bool, fractional_damage: HashMap<u32,f32>,
}
fn read_i(r: &mut impl Read) -> io::Result<i32> { let mut b=[0;4]; r.read_exact(&mut b)?; Ok(i32::from_be_bytes(b)) }
fn read_f(r: &mut impl Read) -> io::Result<f32> { Ok(f32::from_bits(read_i(r)? as u32)) }
fn put_i(w: &mut impl Write, n:i32) -> io::Result<()> { w.write_all(&n.to_be_bytes()) }
fn put_f(w: &mut impl Write, n:f32) -> io::Result<()> { put_i(w,n.to_bits() as i32) }
fn read_anm(path: &Path) -> anm0::Anm0 {
    let bytes=std::fs::read(path).unwrap_or_else(|e| panic!("{}: {e}",path.display()));
    anm0::Anm0::parse(&bytes).expect("Invalid ANM metadata")
}
impl Host {
    fn new(path: &Path, entry:i32, difficulty:u8, seed:u16, character:u8, shot:u8) -> Self {
        let ecl=Ecl::parse(std::fs::read(path).expect("Read ECL")).expect("Parse ECL");
        let dir=path.parent().unwrap();
        let bullets=read_anm(&dir.join("etama3.anm"));
        let mut heights=[8.;10]; let mut widths=[8.;10];
        for (i, base) in BULLET_BASE_SPRITE.iter().enumerate() {
            if let Some(sp)=bullets.entries.iter().flat_map(|e|e.sprites.iter()).find(|s|s.index==*base) {
                heights[i]=sp.height; widths[i]=sp.width;
            }
        }
        let effects=read_anm(&dir.join("etama4.anm"));
        let eff_scripts=effects.entries.into_iter().flat_map(|e|e.scripts).collect();
        let stage=path.file_stem().unwrap().to_string_lossy().chars().last().unwrap();
        let mut scripts=HashMap::new(); let mut sizes=HashMap::new();
        for filename in [format!("stg{stage}enm.anm"),format!("stg{stage}enm2.anm")] {
            let p=dir.join(filename); if !p.exists() {continue;}
            for entry in read_anm(&p).entries {
                for sp in entry.sprites {sizes.insert(sp.index,(sp.width,sp.height));}
                for (id, code) in entry.scripts {scripts.insert(id as i32,code);}
            }
        }
        let world=World {bridge_next_bullet:0, rng:Rng::new(seed),difficulty,rank:16,sub_rank:0,min_rank:16,max_rank:16,
            player_pos:[192.,384.],bullets:vec![],lasers:vec![],events:vec![],pending_spawns:vec![],kill_trash:false,
            boss_present:false,power:128,character,shot_type:shot,time_stopped:false,bomb_active:false,
            bullet_heights:heights,bullet_widths:widths,effects:vec![],eff_scripts,random_item_spawn_index:0,random_item_table_index:0};
        let mut host=Self {ecl,world,enemies:vec![],generations:vec![],anims:vec![],sub_anims:vec![],scripts,sizes,
            frame:0,main_started:false,intro_interrupted:false,fractional_damage:HashMap::new()};
        let mut pos=[192.,96.,0.]; let mut life=-1;
        // Read the original timeline's spawn parameters rather than inventing boss HP.
        let mut off=host.ecl.timeline_offset;
        for _ in 0..20000 {
            let Some(t)=host.ecl.timeline_at(off) else {break};
            if t.time < 0 || t.size < 8 {break;}
            if (0..=7).contains(&t.opcode) && t.arg0 as i32==entry && t.args.len()>=20 {
                pos=[t.arg_f32(0),t.arg_f32(4),t.arg_f32(8)];
                if t.opcode%2==0 {life=t.arg_u16(12) as i16;} break;
            }
            off+=t.size as u32;
        }
        if pos.iter().any(|p|!p.is_finite() || *p < -900.) {pos=[192.,96.,0.];}
        let req=SpawnReq{sub:entry,pos,life,item:-1,score:-1,mirror:false};
        let enemy=Enemy::spawn(&host.ecl,&mut host.world,&req).expect("Invalid boss entry subroutine");
        host.insert(enemy); host
    }
    fn insert(&mut self, enemy:Enemy) {
        if let Some(slot)=self.enemies.iter().position(|e|!e.occupied) {
            self.enemies[slot]=enemy; self.generations[slot]=self.generations[slot].wrapping_add(1);
            self.anims[slot]=None; self.sub_anims[slot]=Default::default();
        } else if self.enemies.len()<256 {
            self.enemies.push(enemy);self.generations.push(1);self.anims.push(None);self.sub_anims.push(Default::default());
        } else {panic!("Original enemy slot pool exhausted");}
    }
    fn id(&self,slot:usize)->u32 {(self.generations[slot]<<8)|slot as u32}
    fn flush_spawns(&mut self) {
        let mut guard=0;
        while !self.world.pending_spawns.is_empty() {
            guard+=1; assert!(guard<1024,"Recursive spawn overflow");
            let req=self.world.pending_spawns.remove(0);
            if let Some(e)=Enemy::spawn(&self.ecl,&mut self.world,&req) {self.insert(e);}
        }
    }
    fn apply_kill_trash(&mut self) {
        if !self.world.kill_trash{return;}
        self.world.kill_trash=false;
        for e in &mut self.enemies {if e.occupied && !e.is_boss {e.kill_as_trash(&self.ecl);}}
    }
    fn tick(&mut self, input:&mut impl Read)->io::Result<()> {
        self.world.player_pos=[read_f(input)?,read_f(input)?];
        let bomb=read_i(input)?!=0; self.world.bomb_active=bomb;
        if bomb {self.world.bullets.clear();for l in &mut self.world.lasers {l.in_use=false;}}
        let damage_count=read_i(input)?; assert!((0..=256).contains(&damage_count));
        for _ in 0..damage_count {
            let id=read_i(input)? as u32; let damage=read_f(input)?; let slot=(id&255) as usize;
            if slot<self.enemies.len() && self.id(slot)==id {
                let e=&mut self.enemies[slot];
                if e.occupied && e.interactable && e.damageable && damage.is_finite() && damage>0. {
                    let remainder=self.fractional_damage.entry(id).or_default(); *remainder+=damage;
                    let applied=remainder.floor() as i32; *remainder-=applied as f32; e.bridge_damage(applied);
                }
            }
        }
        let consumed_count=read_i(input)?; assert!((0..=10000).contains(&consumed_count));
        let mut consumed=HashSet::new(); for _ in 0..consumed_count {consumed.insert(read_i(input)? as u32);}
        self.world.bullets.retain(|b|!consumed.contains(&b.bridge_id));
        self.frame+=1;
        // Dialogue is skipped in practice; original intro motion still runs for two seconds.
        if self.frame>=120 && self.frame%60==0 && !self.main_started {
            self.enemies[0].bridge_interrupt_intro(&self.ecl);self.intro_interrupted=true;
        }
        let mut i=0;
        while i<self.enemies.len() {
            if !self.enemies[i].occupied {i+=1;continue;}
            if self.enemies[i].life<=0 && self.enemies[i].interactable {
                self.enemies[i].spawn_death_effects(&mut self.world);
                self.enemies[i].on_death(&self.ecl,&mut self.world);
            }
            if !self.enemies[i].occupied {i+=1;continue;}
            self.enemies[i].frame_move();
            let size=self.anims[i].as_ref().and_then(|a|a.sprite).and_then(|s|self.sizes.get(&s)).copied().unwrap_or((0.,0.));
            if !self.enemies[i].update_bounds(size.0,size.1) {self.enemies[i].despawn(&mut self.world);i+=1;continue;}
            self.enemies[i].bridge_timeouts();
            self.enemies[i].handle_callbacks(&self.ecl,&mut self.world);self.apply_kill_trash();
            self.enemies[i].run_ecl(&self.ecl,&mut self.world);self.enemies[i].clamp();
            self.apply_kill_trash();self.flush_spawns();
            if !self.world.time_stopped {self.enemies[i].tick_boss_timer();}
            if self.enemies[i].anm_dirty {
                self.enemies[i].anm_dirty=false;
                self.anims[i]=self.scripts.get(&self.enemies[i].anm_script).map(|s|AnmRunner::new(s.clone()));
            }
            if let Some(n)=self.enemies[i].pending_interrupt.take() {if n>=0 {if let Some(a)=&mut self.anims[i] {a.interrupt(n as u32);}}}
            if let Some(a)=&mut self.anims[i] {a.tick();if let Some((base,range))=a.take_pending_random(){a.sprite=Some(base+self.world.rng.u16_in_range(range) as u32);}}
            for s in 0..8 {
                if self.enemies[i].sub_dirty[s] {self.enemies[i].sub_dirty[s]=false;self.sub_anims[i][s]=self.scripts.get(&self.enemies[i].sub_scripts[s]).map(|s|AnmRunner::new(s.clone()));}
                if let Some(a)=&mut self.sub_anims[i][s] {a.tick();if let Some((base,range))=a.take_pending_random(){a.sprite=Some(base+self.world.rng.u16_in_range(range) as u32);}}
            }
            self.enemies[i].update_effects(&mut self.world);i+=1;
        }
        self.flush_spawns();self.world.update_effects();self.update_bullets();
        if self.enemies.iter().any(|e|e.is_boss && e.occupied && e.damageable && e.interactable && e.life>0) {self.main_started=true;}
        Ok(())
    }
    fn snapshot(&mut self, output:&mut impl Write)->io::Result<()> {
        let root_done=self.enemies.first().is_none_or(|e|!e.occupied || (e.life<=1 && !e.damageable && !e.collidable && !self.world.boss_present));
        let complete=self.main_started && root_done;
        put_i(output,0x5342564b)?;put_i(output,if complete {1}else{0})?;
        put_i(output,self.enemies.first().map(|e|e.bridge_phase).unwrap_or(0))?;
        let spells:Vec<i32>=self.world.events.iter().filter_map(|e|if let WorldEvent::SpellcardStart(id,_,_)=e{Some(*id)}else{None}).collect();
        put_i(output,spells.len() as i32)?;for id in spells {put_i(output,id)?;}
        put_i(output,self.world.events.iter().filter(|e|matches!(e,WorldEvent::SpellcardEnd)).count() as i32)?;
        let cancel=self.world.events.iter().any(|e|matches!(e,WorldEvent::BulletCancel));
        if cancel {self.world.bullets.clear();for l in &mut self.world.lasers {l.in_use=false;}}
        let live:Vec<usize>=self.enemies.iter().enumerate().filter_map(|(i,e)|if e.occupied {Some(i)}else{None}).collect();
        put_i(output,live.len() as i32)?;
        for i in live {let e=&self.enemies[i];put_i(output,self.id(i) as i32)?;
            let (hp,max_hp)=e.bridge_health();
            put_f(output,e.pos[0])?;put_f(output,e.pos[1])?;put_f(output,hp as f32)?;put_f(output,max_hp as f32)?;
            put_f(output,e.hitbox[0])?;put_f(output,e.hitbox[1])?;
            let flags=(e.interactable as i32)|((e.damageable as i32)<<1)|((e.collidable as i32)<<2)|((e.invisible as i32)<<3)|((e.is_boss as i32)<<4);
            put_i(output,flags)?;
        }
        put_i(output,self.world.bullets.len() as i32)?;
        for b in &self.world.bullets {put_i(output,b.bridge_id as i32)?;put_f(output,b.pos[0])?;put_f(output,b.pos[1])?;put_f(output,b.angle)?;
            put_i(output,b.sprite as i32)?;put_f(output,b.width)?;put_f(output,b.height)?;put_i(output,if b.spawn_delay==0 && b.despawn_timer==0 {1}else{0})?;
        }
        let live:Vec<_>=self.world.lasers.iter().filter(|l|l.in_use).collect();put_i(output,live.len() as i32)?;
        for l in live {put_f(output,l.pos[0]+l.angle.cos()*l.start_offset)?;put_f(output,l.pos[1]+l.angle.sin()*l.start_offset)?;
            put_f(output,l.pos[0]+l.angle.cos()*l.end_offset)?;put_f(output,l.pos[1]+l.angle.sin()*l.end_offset)?;
            put_f(output,l.width)?;put_i(output,if l.state==1 || (l.state==0 && l.timer>=l.hitbox_start) {1}else{0})?;put_i(output,l.color)?;
        }
        self.world.events.clear();output.flush()
    }
}
fn main()->io::Result<()> {
    let args:Vec<String>=std::env::args().collect(); assert!(args.len()==8,"Usage: host ecl sub difficulty seed character shot reserved");
    let path=PathBuf::from(&args[1]);
    let mut host=Host::new(&path,args[2].parse().unwrap(),args[3].parse().unwrap(),args[4].parse().unwrap(),args[5].parse().unwrap(),args[6].parse().unwrap());
    let mut input=BufReader::new(io::stdin().lock());let mut output=BufWriter::new(io::stdout().lock());
    put_i(&mut output,0x54483036)?;put_i(&mut output,3)?;output.flush()?;
    loop {let command=match read_i(&mut input){Ok(c)=>c,Err(e) if e.kind()==io::ErrorKind::UnexpectedEof=>break,Err(e)=>return Err(e)};
        if command==2 {break;} assert!(command==1,"Invalid frame command");host.tick(&mut input)?;host.snapshot(&mut output)?;
    } Ok(())
}
