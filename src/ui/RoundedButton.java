import javax.swing.*;
import java.awt.*;

public class RoundedButton extends JButton {

    public RoundedButton(String text){
        super(text);
        setFont(new Font("Segoe UI", Font.BOLD, 18));
        setForeground(Color.WHITE);
        setBackground(new Color(0,120,215));
        setFocusPainted(false);
    }

    protected void paintComponent(Graphics g){
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(getBackground());
        g2.fillRoundRect(0,0,getWidth(),getHeight(),30,30);
        super.paintComponent(g);
    }
}