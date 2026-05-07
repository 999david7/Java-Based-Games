public class Question {
    String text;
    String[] options;
    int correct;
    String image;

    public Question(String t,String[] o,int c,String img){
        text=t;
        options=o;
        correct=c;
        image=img;
    }
}