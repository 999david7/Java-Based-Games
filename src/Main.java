import javax.swing.*;
import java.awt.*;
import java.util.List;

public class Main extends JFrame {

    private CardLayout layout;
    private JPanel root;

    private JLabel questionLabel, imageLabel, progressLabel, timerLabel;
    private RoundedButton[] answers;

    private List<Question> questions;
    private int index = 0;
    private int score = 0;
    private int timeLeft = 10;
    private Timer timer;

    public Main() {
        setTitle("Quiz Master Pro");
        setSize(1000, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        layout = new CardLayout();
        root = new JPanel(layout);

        root.add(menu(), "menu");
        root.add(game(), "game");

        add(root);
        setVisible(true);
    }

    private JPanel menu() {
        JPanel p = new JPanel();
        p.setBackground(new Color(20,20,20));
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("QUIZ MASTER");
        title.setFont(new Font("Segoe UI", Font.BOLD, 40));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        RoundedButton play = new RoundedButton("PLAY");
        play.addActionListener(e -> start());

        p.add(Box.createVerticalGlue());
        p.add(title);
        p.add(Box.createRigidArea(new Dimension(0,30)));
        p.add(play);
        p.add(Box.createVerticalGlue());

        return p;
    }

    private JPanel game() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.BLACK);

        JPanel top = new JPanel(new BorderLayout());
        progressLabel = new JLabel("1/10");
        progressLabel.setForeground(Color.WHITE);

        timerLabel = new JLabel("⏱ 10", SwingConstants.RIGHT);
        timerLabel.setForeground(Color.WHITE);

        top.add(progressLabel, BorderLayout.WEST);
        top.add(timerLabel, BorderLayout.EAST);

        panel.add(top, BorderLayout.NORTH);

        JPanel center = new RoundedPanel(30);
        center.setLayout(new BorderLayout());
        center.setBackground(new Color(30,30,30));

        questionLabel = new JLabel("", SwingConstants.CENTER);
        questionLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        questionLabel.setForeground(Color.WHITE);

        imageLabel = new JLabel("", SwingConstants.CENTER);

        center.add(questionLabel, BorderLayout.NORTH);
        center.add(imageLabel, BorderLayout.CENTER);

        panel.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new GridLayout(2,2,15,15));
        answers = new RoundedButton[4];

        for(int i=0;i<4;i++){
            int idx = i;
            answers[i] = new RoundedButton("");
            answers[i].addActionListener(e -> answer(idx));
            bottom.add(answers[i]);
        }

        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    private void start(){
        questions = QuestionBank.get();
        index = 0;
        score = 0;
        next();
        layout.show(root, "game");
    }

    private void next(){
        if(index >= questions.size()){
            JOptionPane.showMessageDialog(this,"Score: "+score);
            layout.show(root,"menu");
            return;
        }

        Question q = questions.get(index);

        progressLabel.setText((index+1)+"/"+questions.size());
        questionLabel.setText(q.text);

        for(int i=0;i<4;i++){
            answers[i].setText(q.options[i]);
        }

        if(q.image != null){
            imageLabel.setIcon(new ImageIcon(q.image));
        } else imageLabel.setIcon(null);

        startTimer();
    }

    private void startTimer(){
        timeLeft = 10;
        timerLabel.setText("⏱ "+timeLeft);

        if(timer!=null) timer.stop();

        timer = new Timer(1000,e->{
            timeLeft--;
            timerLabel.setText("⏱ "+timeLeft);

            if(timeLeft==0){
                timer.stop();
                reveal(-1);
            }
        });
        timer.start();
    }

    private void answer(int i){
        timer.stop();
        reveal(i);
    }

    private void reveal(int i){
        Question q = questions.get(index);

        if(i == q.correct) score += timeLeft * 10;

        for(int j=0;j<4;j++){
            if(j==q.correct) answers[j].setBackground(Color.GREEN);
            else if(j==i) answers[j].setBackground(Color.RED);
        }

        new Timer(1000,e->{
            ((Timer)e.getSource()).stop();
            index++;
            next();
        }).start();
    }

    public static void main(String[] args){
        SwingUtilities.invokeLater(Main::new);
    }
}