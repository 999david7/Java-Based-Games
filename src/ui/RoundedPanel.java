import javax.swing.*;
import java.awt.*;

public class RoundedPanel extends JPanel {
    int radius;

    public RoundedPanel(int r){
        radius=r;
        setOpaque(false);
    }

    protected void paintComponent(Graphics g){
        Graphics2D g2=(Graphics2D)g;
        g2.setColor(getBackground());
        g2.fillRoundRect(0,0,getWidth(),getHeight(),radius,radius);
        super.paintComponent(g);
    }
}