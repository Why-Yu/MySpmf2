package cn.whyyu.myspmf;

import ca.pfv.spmf.algorithms.sequenceprediction.ipredict.database.Item;
import ca.pfv.spmf.algorithms.sequenceprediction.ipredict.database.Sequence;
import ca.pfv.spmf.algorithms.sequenceprediction.ipredict.database.SequenceDatabase;
import ca.pfv.spmf.algorithms.sequenceprediction.ipredict.database.SequenceStatsGenerator;
import ca.pfv.spmf.algorithms.sequenceprediction.ipredict.predictor.CPT.CPT.CPTPredictor;
import ca.pfv.spmf.algorithms.sequenceprediction.ipredict.predictor.Markov.MarkovAllKPredictor;
import ca.pfv.spmf.algorithms.sequentialpatterns.spam.AlgoCMSPAM;
import ca.pfv.spmf.test.MainTestCMSPAM_saveToFile;

import java.io.*;
import java.net.URL;
import java.util.*;

public class Predictor {
    public static void main(String[] args) throws IOException {
        //-------分割子数据集，5%作为查询轨迹，95%作为训练轨迹
//        String name = "90000.txt";
////        String input = fileToPath(name);
//        String input = "F:/论文/毕业论文/spmfData/100000spmf/cutData/" + name;
//        BufferedReader myInput = new BufferedReader(new FileReader(input));
//        String thisLine;
//        int sequenceCount = 0;
//        while ((thisLine = myInput.readLine()) != null) {
//            sequenceCount++;
//        }
//        myInput.close();
//
//        int totalNumber = (int) (sequenceCount * 0.05);
//        Set<Integer> numberSet = new HashSet<>();
//        Random random = new Random();
//
//        while(numberSet.size() < totalNumber) {
//            int i = random.nextInt(sequenceCount);
//            numberSet.add(i);
//        }
//
//        LineNumberReader lnr = new LineNumberReader(new FileReader(input));
//        BufferedWriter myOutputQuery = new BufferedWriter(new FileWriter("F:/论文/毕业论文/spmfData/100000spmf/cutQuery/" + name));
//        BufferedWriter myOutputTrain = new BufferedWriter(new FileWriter("F:/论文/毕业论文/spmfData/100000spmf/cutTrain/" + name));
//        while ((thisLine = lnr.readLine()) != null) {
//            if (numberSet.contains(lnr.getLineNumber())) {
//                myOutputQuery.write(thisLine + System.lineSeparator());
//            } else {
//                myOutputTrain.write(thisLine + System.lineSeparator());
//            }
//        }
//        lnr.close();
//        myOutputQuery.flush();
//        myOutputQuery.close();
//        myOutputTrain.flush();
//        myOutputTrain.close();
        //-------

        //------ 挖掘频繁序列
//        String input = "F:/论文/毕业论文/spmfData/100000spmf/train/9.txt";
//        String output = "F:/论文/毕业论文/spmfData/100000spmf/frequent/frequent9.txt";
//
//        // Create an instance of the algorithm
//        AlgoCMSPAM algo = new AlgoCMSPAM();
//
//        // This optional parameter allows to specify the minimum pattern length:
//        algo.setMinimumPatternLength(2);  // optional
//
//        // This optional parameter allows to specify the max gap between two
//        // itemsets in a pattern. If set to 1, only patterns of contiguous itemsets
//        // will be found (no gap).
////		algo.setMaxGap(1);
//
//        // if you set the following parameter to true, the sequence ids of the sequences where
//        // each pattern appears will be shown in the result
//        boolean outputSequenceIdentifiers = false;
//
//        // execute the algorithm with minsup = 2 sequences  (50 %)
//        algo.runAlgorithm(input, output, 0.0002, outputSequenceIdentifiers);     // minsup = 106   k = 1000   BMS
//        algo.printStatistics();
        //------

        //------轨迹预测
        String inputPath = "F:/论文/毕业论文/spmfData/100000spmf/frequent/frequent2.txt";
        SequenceDatabase trainingSet = new SequenceDatabase();
        trainingSet.loadMyResult(inputPath, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);

        Set<Integer> frequentItems = SequenceStatsGenerator.modPrinStats(trainingSet, " training sequences ");
//        System.out.println(frequentItems.size());
        String optionalParameters1 = "splitLength:6 splitMethod:0 recursiveDividerMin:1 recursiveDividerMax:5";

        CPTPredictor predictionModel = new CPTPredictor("CPT", optionalParameters1);
        predictionModel.Train(trainingSet.getSequences());

        String optionalParameters2 ="order:5";
        MarkovAllKPredictor predictor = new MarkovAllKPredictor("Markov", optionalParameters2);
        predictor.Train(trainingSet.getSequences());
//
        String queryPath = "F:/论文/毕业论文/spmfData/100000spmf/query/2.txt";
        BufferedReader myQueryInput = new BufferedReader(new FileReader(queryPath));
        String thisLine;
        double total = 0.0;
        double matchCPT = 0.0;
        double matchMarkov = 0.0;

        long timeCPT = 0;
        long timeMarkov = 0;
//        Random random = new Random();
        while ((thisLine = myQueryInput.readLine()) != null) {

            Sequence sequence = new Sequence(1);
            Sequence querySequence = new Sequence(2);
            for (String token : thisLine.split(" ")) {
                if (!token.equals("-1") && !token.equals("-2")) {
                    sequence.addItem(new Item(Integer.parseInt(token)));
                }
            }

            for (Item item : sequence.getItems()) {
                if (frequentItems.contains(item.val)) {
                    querySequence.addItem(item);
                }
            }

            Sequence prefix = new Sequence(3);
            Set<Integer> matchSymbolSet = new HashSet<>();
            int nLength = querySequence.size() / 6;
            if (querySequence.size() == 0) {
                continue;
            }

            if (nLength >= 1) {
                for (int i = 0 ; i < 5 ; i++) {
                    prefix.addItem(querySequence.get(i));
                }
//                total++;
                for (int j = 5 ; j < querySequence.size() ; j++) {
                    matchSymbolSet.add(querySequence.get(j).val);
                }
//                if (predict(predictionModel, prefix, matchSymbolSet, 15)) {
//                    matchCPT++;
//                }
//                if (predictMarkov(predictor, prefix, matchSymbolSet)) {
//                    matchMarkov++;
//                }

                timeCPT += predict(predictionModel, prefix, matchSymbolSet, 15);
                timeMarkov += predictMarkov(predictor, prefix, matchSymbolSet);
            }
            else if (querySequence.size() > 1) {
                for (int i = 0; i < querySequence.size() - 1; i++) {
                    prefix.addItem(querySequence.get(i));
                }
//                System.out.println(querySequence);
//                System.out.println(prefix);
//                matchSymbolSet.add(querySequence.get(querySequence.size() - 1).val);
//                total++;
//                if (predict(predictionModel, prefix, matchSymbolSet, 12)) {
//                    matchCPT++;
//                }
//                if (predictMarkov(predictor, prefix, matchSymbolSet)) {
//                    matchMarkov++;
//                }

//                timeCPT += predict(predictionModel, prefix, matchSymbolSet, 15);
//                timeMarkov += predictMarkov(predictor, prefix, matchSymbolSet);
            }
        }

//        System.out.println(matchCPT);
//        System.out.println(matchMarkov);
        System.out.println(timeCPT);
        System.out.println(timeMarkov);
//        System.out.println(total);

//        System.out.println(matchCPT / total);
//        System.out.println(matchMarkov / total);
        myQueryInput.close();



//
//        Sequence sequence1 = new Sequence(1);
//        sequence1.addItem(new Item(557807));
//        sequence1.addItem(new Item(557808));
//
//        predictionModel.Predict(sequence1);
//        Map<Integer, Float> countTable1 = predictionModel.getCountTable();
//        List<Float> scoreList = new ArrayList<>();
//        for(Map.Entry<Integer,Float> entry : countTable1.entrySet()){
//            scoreList.add(entry.getValue());
//        }
//        scoreList.sort(Collections.reverseOrder());
//        System.out.println(scoreList);


        //------

//        String inputPath = "F:/论文/毕业论文/spmfData/100000spmf/train/1.txt";
//        BufferedReader myQueryInput = new BufferedReader(new FileReader(inputPath));
//        Set<Integer> set = new HashSet<>();
//        String thisLine;
//        while ((thisLine = myQueryInput.readLine()) != null) {
//            for (String token : thisLine.split(" ")) {
//                if (!token.equals("-1") && !token.equals("-2")) {
//                    set.add(Integer.parseInt(token));
//                }
//            }
//        }
        // 1664
//        System.out.println(set.size());
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestCMSPAM_saveToFile.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
    }

    public static long predict(CPTPredictor predictionModel, Sequence prefix,
                                  Set<Integer> matchSymbolSet, int topN) {
        // record start time
        long startTime = System.nanoTime();

        Sequence thePrediction = predictionModel.Predict(prefix);

        Map<Integer, Float> countTable = predictionModel.getCountTable();
        List<Result> scoreList = new ArrayList<>();
        for(Map.Entry<Integer,Float> entry : countTable.entrySet()){
            scoreList.add(new Result(entry.getKey(), entry.getValue()));
        }
        scoreList.sort(Collections.reverseOrder());
        List<Result> topList;
        if (scoreList.size() > topN) {
            topList = scoreList.subList(0,topN);
        } else {
            topList = scoreList;
        }
//        System.out.println(topList);
        Set<Integer> predictSet = new HashSet<>();
        for (Result result : topList) {
            predictSet.add(result.getKey());
        }
        long endTime = System.nanoTime();
        return (endTime - startTime) / 1000000;
//        if (thePrediction.size() != 0) {
//            for (int symbol : matchSymbolSet) {
//                if (predictSet.contains(symbol)) {
//                    return true;
//                }
//            }
//        }
//        return false;
    }

    public static long predictMarkov(MarkovAllKPredictor predictor, Sequence prefix,
                                         Set<Integer> matchSymbolSet) {
        long startTime = System.nanoTime();
//        Sequence result = predictor.Predict(prefix);
        Set<Integer> result = predictor.modPredict(prefix);
//        if (result.size() != 0) {
//            for (int symbol : matchSymbolSet) {
//                if (result.contains(symbol)) {
//                    return true;
//                }
//            }
//        }
        long endTime = System.nanoTime();
        return (endTime - startTime) / 1000000;
//        return matchSymbolSet.contains(result.get(0).val);
//        return false;
    }
}
