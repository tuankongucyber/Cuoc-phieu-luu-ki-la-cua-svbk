// Adapted from upstream stage.rs; see UPSTREAM and retained MIT notice.
use crate::ecl_vm::*;
use crate::Host;
impl Host {
    pub(crate) fn update_bullets(&mut self) {
        // Sakuya's time-stop: bullets and lasers freeze in place (the boss keeps
        // moving and laying down new ones over the frozen field).
        if self.world.time_stopped {
            return;
        }
        let player = self.world.player_pos;
        for b in &mut self.world.bullets {
            // BULLET_STATE_DESPAWNING (BulletManager.cpp:947): a fading bullet
            // drifts at half velocity over the 12-frame donut anm, then is
            // removed (no ex-behaviour, no cull). (#14)
            if b.despawn_timer > 0 {
                b.pos[0] += b.angle.cos() * b.speed * 0.5;
                b.pos[1] += b.angle.sin() * b.speed * 0.5;
                b.despawn_timer -= 1;
                if b.despawn_timer == 0 {
                    b.oob_count = i32::MAX; // swept by the retain below
                }
                continue;
            }
            b.timer += 1;
            // Ex behaviors, ported from BulletManager::OnUpdate.
            if b.ex_flags & 1 != 0 {
                if b.timer <= 16 {
                    // Spawn boost: extra speed decaying 5 -> 0 over 16 frames.
                    let boost = 5.0 - b.timer as f32 * 5.0 / 16.0;
                    b.pos[0] += b.angle.cos() * boost;
                    b.pos[1] += b.angle.sin() * boost;
                } else {
                    b.ex_flags &= !1;
                }
            } else if b.ex_flags & 0x10 != 0 {
                if b.timer >= b.ex_int0 {
                    b.ex_flags &= !0x10;
                } else {
                    let vx = b.angle.cos() * b.speed + b.ex_accel[0];
                    let vy = b.angle.sin() * b.speed + b.ex_accel[1];
                    b.speed = (vx * vx + vy * vy).sqrt();
                    b.angle = vy.atan2(vx);
                }
            } else if b.ex_flags & 0x20 != 0 {
                if b.timer >= b.ex_int0 {
                    b.ex_flags &= !0x20;
                } else {
                    b.angle += b.ex_f[1];
                    b.speed += b.ex_f[0];
                }
            }
            // Direction-change group (exFlags & 0x1c0): every `ex_int0` frames,
            // up to `ex_int1` times, re-point the bullet and reset its speed;
            // between changes the speed ramps down to a near-stop. The three
            // modes differ only in how the new angle is chosen:
            //   0x40  rotate by a fixed delta   (angle += rotation)
            //   0x100 snap to an absolute angle (angle  = rotation)
            //   0x80  re-aim at the player      (angle  = atan2(player) + rotation)
            // 0x80 is Cirno's Perfect Freeze: shards freeze, then home.
            let mut move_speed = b.speed;
            let mode = b.ex_flags & 0x1c0;
            if mode != 0 {
                let interval = b.ex_int0;
                let trigger = if interval > 0 {
                    b.timer >= interval * (b.ex_count + 1)
                } else {
                    true
                };
                if trigger {
                    b.ex_count += 1;
                    if b.ex_count >= b.ex_int1 {
                        b.ex_flags &= !mode;
                    }
                    b.angle = if mode & 0x40 != 0 {
                        b.angle + b.ex_f[0]
                    } else if mode & 0x100 != 0 {
                        b.ex_f[0]
                    } else {
                        (player[1] - b.pos[1]).atan2(player[0] - b.pos[0]) + b.ex_f[0]
                    };
                    b.speed = b.ex_f[1];
                    move_speed = b.speed;
                } else {
                    let t = b.timer as f32 - (interval * b.ex_count) as f32;
                    move_speed = b.speed - t * b.speed / interval as f32;
                }
            } else if b.ex_flags & 0xc00 != 0 {
                // Wall-bounce group (BulletManager.cpp:831): once the bullet is
                // fully off the playfield, reflect its angle off the crossed
                // edge(s), reset its speed to dirChangeSpeed (ex_f[1]) and count
                // the bounce; clear the flag after dirChangeMaxTimes (ex_int1)
                // bounces. 0x400 bounces all four edges; 0x800 skips the bottom.
                let (hw, hh) = (b.width / 2.0, b.height / 2.0);
                let in_bounds = b.pos[0] + hw >= 0.0
                    && b.pos[0] - hw <= FIELD_W
                    && b.pos[1] + hh >= 0.0
                    && b.pos[1] - hh <= FIELD_H;
                if !in_bounds {
                    let bounce_bottom = b.ex_flags & 0x400 != 0;
                    if b.pos[0] < 0.0 || b.pos[0] >= FIELD_W {
                        b.angle = normalize_angle(-b.angle - std::f32::consts::PI);
                    }
                    if b.pos[1] < 0.0 || (bounce_bottom && b.pos[1] >= FIELD_H) {
                        b.angle = -b.angle;
                    }
                    b.speed = b.ex_f[1];
                    move_speed = b.speed;
                    b.ex_count += 1;
                    if b.ex_count >= b.ex_int1 {
                        b.ex_flags &= !0xc00;
                    }
                }
            }
            let factor = if b.spawn_delay > 0 {
                b.spawn_delay -= 1;
                b.spawn_factor
            } else {
                1.0
            };
            b.pos[0] += b.angle.cos() * move_speed * factor;
            b.pos[1] += b.angle.sin() * move_speed * factor;
            // On the spawn->fired transition frame the decomp falls through into
            // the FIRED case and applies a second, full-velocity move
            // (BulletManager.cpp:698 then :895).
            if factor != 1.0 && b.spawn_delay == 0 {
                b.pos[0] += b.angle.cos() * move_speed;
                b.pos[1] += b.angle.sin() * move_speed;
            }
            // Off-screen cull (BulletManager.cpp:896, only for FIRED bullets):
            // GameManager::IsInBounds — the sprite (w x h, ~square) must overlap
            // [0,FIELD_W] x [0,FIELD_H]. Normal bullets die on the first OOB frame;
            // dir-change/bounce bullets (0x40/0x80/0x100/0x400/0x800) get 256.
            if b.spawn_delay == 0 {
                let (hw, hh) = (b.width / 2.0, b.height / 2.0);
                let in_bounds = b.pos[0] + hw >= 0.0
                    && b.pos[0] - hw <= FIELD_W
                    && b.pos[1] + hh >= 0.0
                    && b.pos[1] - hh <= FIELD_H;
                let roamer = b.ex_flags & 0xdc0 != 0;
                if in_bounds {
                    b.oob_count = 0;
                } else if !roamer && b.oob_count == 0 {
                    b.oob_count = i32::MAX;
                } else {
                    b.oob_count += 1;
                }
            }
        }
        self.world.bullets.retain(|b| b.oob_count < 256);

        // Lasers (state machine from BulletManager::OnUpdate).
        for l in &mut self.world.lasers {
            if !l.in_use {
                continue;
            }
            l.end_offset += l.speed;
            if l.start_length < l.end_offset - l.start_offset {
                l.start_offset = l.end_offset - l.start_length;
            }
            if l.start_offset < 0.0 {
                l.start_offset = 0.0;
            }
            l.timer += 1;
            match l.state {
                0 => {
                    if l.timer >= l.start_time {
                        l.state = 1;
                        l.timer = 0;
                    }
                }
                1 => {
                    if l.timer >= l.duration {
                        l.state = 2;
                        l.timer = 0;
                    }
                }
                _ => {
                    if l.timer >= l.despawn_duration {
                        l.in_use = false;
                    }
                }
            }
        }
    }

}
