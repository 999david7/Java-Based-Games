import java.util.*;

public class QuestionBank {

    public static List<Question> get(){
        List<Question> list = new ArrayList<>();

        list.add(new Question(
                "What is the capital of France?",
                new String[]{"Berlin","Madrid","Paris","Rome"},
                2,
                "images/paris.jpg"
        ));

        list.add(new Question(
                "Who developed Minecraft?",
                new String[]{"EA","Mojang","Ubisoft","Valve"},
                1,
                "images/minecraft.jpg"
        ));

        list.add(new Question(
                "What planet is known as the Red Planet?",
                new String[]{"Earth","Mars","Venus","Jupiter"},
                1,
                "images/mars.jpg"
        ));

        // 🔥 duplicate pattern to reach 50–100+
        for(int i=0;i<50;i++){
            list.add(new Question(
                    "Sample question "+i,
                    new String[]{"A","B","C","D"},
                    i%4,
                    null
            ));
        }

        return list;
    }
}