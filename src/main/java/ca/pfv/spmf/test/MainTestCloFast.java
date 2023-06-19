package ca.pfv.spmf.test;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.clofast.AlgoCloFast;
import ca.pfv.spmf.algorithms.sequentialpatterns.clofast.FastDataset;

/**
 * Example of how to use the CloFast algorithm in source code.
 */
public class MainTestCloFast {

	public static void main(String [] arg) throws IOException{
// ------ 批量计算
//		File dataDirectory = new File("F:/论文/毕业论文/spmfData/100spmf/");
//		File[] files = dataDirectory.listFiles();
//
//		if (files != null) {
//			for (File file : files) {
//				String inputFile = file.getPath();
//				String outputPath = "F:/论文/毕业论文/spmfData/finalResult/" + file.getName();
//				float minsup = 0.002f;
//				AlgoCloFast algorithm = new AlgoCloFast();
//				algorithm.runAlgorithm(inputFile, outputPath, minsup);
//				algorithm.printStatistics();
//			}
//		}

// ------ 单独计算
//		 input file
		String inputFile = fileToPath("newAll10000.txt");
		
//		 output file path
		String outputPath = ".//outputCloFAST.txt";

//		 Create an instance of the algorithm with minsup = 50 %
		float minsup = 0.001f;
		
//		=== THIS IS THE FIRST WAY OF RUNNING CLOFAST =====
//		 It reads the dataset and run the algorithm.
//
//         run the algoritm
        AlgoCloFast algorithm = new AlgoCloFast();
        algorithm.runAlgorithm(inputFile, outputPath, minsup);

        algorithm.printStatistics();
        
//		=== THIS IS THE SECOND WAY OF RUNNING CLOFAST =====
//         It should be used if you want to run CloFast several times
//         on the same dataset. In that case, the total execution time
//         does not include the time for reading the database
        
//		FastDataset dataset = FastDataset.fromPrefixspanSource(inputFile, minsup);
//        CloFast algorithm = new CloFast();
//        algorithm.runAlgorithm(dataset, outputPath, minsup);

	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestCloFast.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}