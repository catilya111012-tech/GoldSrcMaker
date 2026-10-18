import com.goldsrc.maker.scripting.BlockGraph;
import com.goldsrc.maker.scripting.CodeGenerator;

public class PrintScript {
    public static void main(String[] args) {
        System.out.println(CodeGenerator.generate(BlockGraph.createDefault()));
    }
}
