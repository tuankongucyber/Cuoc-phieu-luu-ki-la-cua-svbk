/** Input commands are consumed only by a running simulation tick. */
public final class InputState {
    public boolean up, down, left, right, focus, dashPressed, bombPressed;
    public double dx() { return (right ? 1 : 0) - (left ? 1 : 0); }
    public double dy() { return (down ? 1 : 0) - (up ? 1 : 0); }
    public void clear() { up = down = left = right = focus = dashPressed = bombPressed = false; }
}
