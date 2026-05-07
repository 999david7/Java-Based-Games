import java.awt.*;
import javax.swing.*;
import java.util.Random;

public class Main extends JFrame {
    char player = 'X';
    JButton[] buttons = new JButton[9];
    JCheckBox aiBox = new JCheckBox("Play vs AI");

    public Main() {
        setTitle("Tik Tak Toe");
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        topPanel.add(aiBox);
        add(topPanel, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(3, 3));
        for (int i = 0; i < 9; i++) {
            int index = i;
            buttons[i] = new JButton("");
            buttons[i].setFont(new Font("SansSerif", Font.BOLD, 50));
            buttons[i].setFocusPainted(false);
            buttons[i].addActionListener(e -> {
                if (buttons[index].getText().equals("")) {
                    makeMove(index);
                    if (aiBox.isSelected() && !isFull()) aiMove();
                }
            });
            grid.add(buttons[i]);
        }

        JButton resetBtn = new JButton("Restart Game");
        resetBtn.addActionListener(e -> reset());

        add(grid, BorderLayout.CENTER);
        add(resetBtn, BorderLayout.SOUTH);

        setSize(450, 550);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    void makeMove(int i) {
        buttons[i].setText(String.valueOf(player));
        buttons[i].setForeground(player == 'X' ? Color.BLUE : Color.RED);
        if (checkWin()) {
            JOptionPane.showMessageDialog(this, "Player " + player + " Wins!");
            reset();
        } else {
            player = (player == 'X') ? 'O' : 'X';
        }
    }

    void aiMove() {
        Random r = new Random();
        while (true) {
            int i = r.nextInt(9);
            if (buttons[i].getText().equals("")) {
                makeMove(i);
                break;
            }
        }
    }

    boolean isFull() {
        for (JButton b : buttons) if (b.getText().equals("")) return false;
        return true;
    }

    boolean checkWin() {
        int[][] wins = {{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
        for (int[] w : wins) {
            if (!buttons[w[0]].getText().isEmpty() &&
                    buttons[w[0]].getText().equals(buttons[w[1]].getText()) &&
                    buttons[w[1]].getText().equals(buttons[w[2]].getText())) return true;
        }
        return false;
    }

    void reset() {
        for (JButton b : buttons) b.setText("");
        player = 'X';
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) {}
        new Main();
    }
}