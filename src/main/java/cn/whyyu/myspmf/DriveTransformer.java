package cn.whyyu.myspmf;

import com.google.common.geometry.S1Angle;
import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2LatLng;
import com.google.common.geometry.S2Point;

import java.io.*;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DriveTransformer {
//    private static final double minLat = 39.75;
//    private static final double maxLat = 40.1;
//    private static final double minLng = 116.15;
//    private static final double maxLng = 116.6;
    public static Set<Integer> symbolSet = new HashSet<>();
    public static int sequenceCount = 0;

//    /**
//     * 选择研究范围内的数据，因为完整的数据太大了
//     * @Deprecated 因为这样会把轨迹强行截断，导致截取后的数据文件很奇怪
//     * @param inputFile 输入文件
//     * @param outputFile 输出文件
//     */
//    public static void filterRegion(File inputFile, File outputFile) {
//        try (BufferedReader myInput = new BufferedReader(new FileReader(inputFile));
//             BufferedWriter myOutput = new BufferedWriter(new FileWriter(outputFile));) {
//            String thisLine = null;
//            while ((thisLine = myInput.readLine()) != null) {
//                String[] tokens = thisLine.split(",");
//                double lat = Double.parseDouble(tokens[3]);
//                double lng = Double.parseDouble(tokens[2]);
//                if ( lat > minLat && lat < maxLat && lng > minLng && lng < maxLng) {
//                    myOutput.write(thisLine + System.lineSeparator());
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    /**
     * 排除重复点，并将车辆的连续GPS记录分割为一段段trip，trip路径是有意义的行驶片段<br>
     * (生成的结果会有一些trip只包含一个GPS点且这些trip往往连续出现，其实这是出租车司机在寻找客人，走一小段停住
     * 所以结果要输入融合算法，将一个GPS点的小片段融合)
     * @param inputFile 输入文件
     * @param outputFile 输出文件
     */
    @Deprecated
    public static void generateTrip(File inputFile, File outputFile) {
        String thisLine = null;
        // 记录当前车辆状态，以便利用#分割trip
        boolean  stationaryStatus = false;
        //store t-1 location for stay point detection
        S2LatLng preLocation= null;
        //store t-1 dataTime for exclude same time point
        String preTime = null;
        // 由于浮点数的误差，四舍五入保留五位小数
        DecimalFormat decimalFormat = new DecimalFormat("#.00000");

        //try with resources
        try (BufferedReader myInput = new BufferedReader(new FileReader(inputFile));
             BufferedWriter myOutput = new BufferedWriter(new FileWriter(outputFile));) {
            thisLine = myInput.readLine();
            String[] preToken = thisLine.split(",");
            preTime = preToken[1];
            preLocation = S2LatLng.fromDegrees(Double.parseDouble(preToken[3]),
                    Double.parseDouble(preToken[2]));
            myOutput.write(decimalFormat.format(preLocation.latDegrees()) + ","
                    + decimalFormat.format(preLocation.lngDegrees()) + System.lineSeparator());

            while ((thisLine = myInput.readLine()) != null) {
                String[] tokens = thisLine.split(",");
                // 忽略相同时间的GPS点
                if (Objects.equals(tokens[1], preTime)) {
                    continue;
                }
                // 忽略错点
                if (Objects.equals(tokens[2], "0.0")) {
                    continue;
                }
                S2LatLng curLocation = S2LatLng.fromDegrees(Double.parseDouble(tokens[3]),
                        Double.parseDouble(tokens[2]));
                boolean preMove = curLocation.getDistance(preLocation, 6371000) > 10;

                // 移动状态
                if (preMove) {
                    myOutput.write(decimalFormat.format(curLocation.latDegrees() )+ ","
                            + decimalFormat.format(curLocation.lngDegrees()) + System.lineSeparator());
                    if (stationaryStatus) {
                        stationaryStatus = false;
                    }
                    // 停止状态
                } else {
                    if (!stationaryStatus) {
                        myOutput.write("#" + System.lineSeparator());
                        stationaryStatus = true;
                    }
                }
                preLocation = curLocation;
                preTime = tokens[1];
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 为了代码编写的简洁，每个文件需要扫描两遍<br>
     * 第一遍扫描记录下分割trip的#的行序号，然后统计需要合并的区间<br>
     * 第二遍扫描执行合并并输出到新文件
     * @param inputFile 输入文件
     * @param outputFile 输出文件
     */
    @Deprecated
    public static void mergeSinglePointTrip(File inputFile, File outputFile) {
        // #出现的行序号
        List<Integer> tripIndex = new ArrayList<>();
        // 需要融合的区间
        Map<Integer, Integer> mergeSection = new TreeMap<>();
        String line;
        try {
            LineNumberReader lnr = new LineNumberReader(new FileReader(inputFile));
            while ((line = lnr.readLine()) != null) {
                if (line.startsWith("#")) {
                    tripIndex.add(lnr.getLineNumber());
                }
            }
            lnr.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 说明该车辆至多只有两段trip样本太少，参考意义不大，直接忽略
        if (tripIndex.size() < 3) {
            return;
        }

        // 生成融合区间
        int count = 0;
        int start = 0;
        int end = 0;
        for (int i = 0 ; i < tripIndex.size() - 1 ; ++i) {
            if (tripIndex.get(i + 1) - tripIndex.get(i) == 2) {
                if (count >= 1) {
                    end = tripIndex.get(i + 1);
                } else {
                    start = tripIndex.get(i) + 1;
                }
                count++;
            } else {
                if (count == 1) {
                    count = 0;
                    start = 0;
                } else if (count > 1) {
                    mergeSection.put(start, end);
                    start = 0;
                    end = 0;
                    count = 0;
                }
            }
        }

        // 依据融合区间，把只有一个GPS点的小片段融合
        StringBuilder tripBuilder = new StringBuilder();
        try ( LineNumberReader lnr = new LineNumberReader(new FileReader(inputFile));
              BufferedWriter myOutput = new BufferedWriter(new FileWriter(outputFile));) {

            while ((line = lnr.readLine()) != null) {
                if (!mergeSection.containsKey(lnr.getLineNumber())) {
                    myOutput.write(line + System.lineSeparator());
                } else {
                    int startLineNumber = lnr.getLineNumber();
                    int endLineNumber = mergeSection.get(startLineNumber);
                    tripBuilder.append(line);
                    tripBuilder.append(System.lineSeparator());
                    for (int i = startLineNumber + 1 ; i < endLineNumber ; ++i) {
                        line = lnr.readLine();
                        if (!line.startsWith("#")) {
                            tripBuilder.append(line);
                            tripBuilder.append(System.lineSeparator());
                        }
                    }
                    myOutput.write(tripBuilder.toString());
                    tripBuilder.setLength(0);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * grid大小设定为level-14,每个网格的边长大约为530m左右
     */
    @Deprecated
    public static void gridFormation(File inputFile, File outputFile) {
        try ( BufferedReader myInput = new BufferedReader(new FileReader(inputFile));
              BufferedWriter myOutput = new BufferedWriter(new FileWriter(outputFile));) {
            String thisLine = null;
            while ((thisLine = myInput.readLine()) != null) {
                if (!thisLine.startsWith("#")) {
                    String[] tokens = thisLine.split(",");
                    double lat = Double.parseDouble(tokens[0]);
                    double lng = Double.parseDouble(tokens[1]);
                    S2LatLng s2LatLng = S2LatLng.fromDegrees(lat, lng);
                    S2CellId s2CellId = S2CellId.fromLatLng(s2LatLng).parent(14);
                    myOutput.write(s2CellId.getPartialKey() + System.lineSeparator());
                } else {
                    myOutput.write(thisLine + System.lineSeparator());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Deprecated
    public static void tDrive2Spmf(File inputFile, File outputFile) {
        String preGrid = null;
        StringBuilder spmfBuilder = new StringBuilder();
        try ( BufferedReader myInput = new BufferedReader(new FileReader(inputFile));
              BufferedWriter myOutput = new BufferedWriter(new FileWriter(outputFile));) {
            String thisLine = null;
            while ((thisLine = myInput.readLine()) != null) {
                if (!thisLine.startsWith("#")) {
                    if (Objects.equals(preGrid, thisLine)) {
                        continue;
                    }
                    spmfBuilder.append(thisLine);
                    spmfBuilder.append(" -1 ");
                    preGrid = thisLine;
                } else {
                    spmfBuilder.append("-2");
                    myOutput.write(spmfBuilder.toString() + System.lineSeparator());
                    spmfBuilder.setLength(0);
                    preGrid = null;
                }
            }
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 修改版的gridFormation，我们想先转换为grid再分割为trip
     * @param inputFile 输入文件
     * @param outputFile 输出文件
     */
    public static void modGridFormation(File inputFile, File outputFile) {
        boolean firstSameTime = false;
        boolean firstAbsencePos = false;
        boolean firstWrongPos = false;
        //store t-1 location for stay point detection
        int preLocation= -1;
        //store t-1 dataTime for exclude same time point
        String preTime = null;
        // 记录当前车辆状态，以便利用#分割trip
        boolean stationaryStatus = false;
        // 因为每条记录间隔时间不一致，所以我们设定只有超过10分钟，并且未跨越网格的才是停车状态
        // 记录最近一次从静止变成运动状态时的dateTime
        LocalDateTime wakeUpDateTime = null;
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String thisLine = null;

        try ( BufferedReader myInput = new BufferedReader(new FileReader(inputFile));
              BufferedWriter myOutput = new BufferedWriter(new FileWriter(outputFile));) {
            thisLine = myInput.readLine();
            String[] preToken = thisLine.split(",");
            preTime = preToken[1];
            wakeUpDateTime = LocalDateTime.parse(preToken[1], dateTimeFormatter);
            double lat = Double.parseDouble(preToken[3]);
            double lng = Double.parseDouble(preToken[2]);
            S2LatLng s2LatLng = S2LatLng.fromDegrees(lat, lng);
            S2CellId s2CellId = S2CellId.fromLatLng(s2LatLng).parent(14);
            preLocation = s2CellId.getPartialKey();
            myOutput.write(preLocation + System.lineSeparator());

            while ((thisLine = myInput.readLine()) != null) {
                String[] tokens = thisLine.split(",");
                // 忽略相同时间的GPS点
                if (Objects.equals(tokens[1], preTime)) {
                    if (!firstSameTime) {
                        firstSameTime = true;
                        System.out.println("相同时间" + tokens[0]);
                    }
                    continue;
                }
                // 忽略缺值点
                if (Objects.equals(tokens[2], "0.0")) {
                    if (!firstAbsencePos) {
                        firstAbsencePos = true;
                        System.out.println("缺值" + tokens[0] + tokens[1]);
                    }
                    continue;
                }
                lat = Double.parseDouble(tokens[3]);
                lng = Double.parseDouble(tokens[2]);
                s2LatLng = S2LatLng.fromDegrees(lat, lng);
                s2CellId = S2CellId.fromLatLng(s2LatLng).parent(14);
                int location = s2CellId.getPartialKey();
                // 忽略经纬度错的离谱的点
                if (location < 0) {
                    if (!firstWrongPos) {
                        firstWrongPos = true;
                        System.out.println("错点" + tokens[0] + tokens[1]);
                    }
                    continue;
                }
                if (location == preLocation) {
                    LocalDateTime nowDateTime = LocalDateTime.parse(tokens[1], dateTimeFormatter);
                    Duration duration = Duration.between(wakeUpDateTime, nowDateTime);
                    // 设置一点误差容忍度，因为部分以10分钟记录一次的数据并不是严格10分钟
                    if (duration.toSeconds() > 590 && !stationaryStatus) {
                        myOutput.write("#" + System.lineSeparator());
                        stationaryStatus = true;
                    }
                } else {
                    myOutput.write(location + System.lineSeparator());
                    if (stationaryStatus) {
                        stationaryStatus = false;
                        wakeUpDateTime = LocalDateTime.parse(tokens[1], dateTimeFormatter);
                    }
                }
                preTime = tokens[1];
                preLocation = location;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int modTDriveSpmf(File inputFile, File outputFile) {
        int count = 0;
        StringBuilder spmfBuilder = new StringBuilder();
        try ( BufferedReader myInput = new BufferedReader(new FileReader(inputFile));
              BufferedWriter myOutput = new BufferedWriter(new FileWriter(outputFile));) {
            String thisLine = null;
            while ((thisLine = myInput.readLine()) != null) {
                if (!thisLine.startsWith("#")) {
                    spmfBuilder.append(thisLine);
                    spmfBuilder.append(" -1 ");
                    symbolSet.add(Integer.parseInt(thisLine));
//                    count++;
                } else {
                    spmfBuilder.append("-2");
                    myOutput.write(spmfBuilder.toString() + System.lineSeparator());
                    spmfBuilder.setLength(0);
                    sequenceCount++;
                }
            }
//            resWriter.write(count + System.lineSeparator());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return count;
    }

    public static void combineSpmf(File inputDirectory, File outputFile, int totalNumber) {
        int count = 1;
        try {
            File[] files = inputDirectory.listFiles();
            BufferedWriter myOutput = new BufferedWriter(new FileWriter(outputFile));
            if (files != null) {
                for (File file : files) {
                    if (count > totalNumber) {
                        break;
                    } else {
                        count++;
                    }
                    BufferedReader myInput = new BufferedReader(new FileReader(file));
                    String thisLine = null;
                    while ((thisLine = myInput.readLine()) != null) {
                        myOutput.write(thisLine + System.lineSeparator());
                    }
                    myInput.close();
                }
            }
            myOutput.flush();
            myOutput.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void cutSpmf(File inputFile, File outputFile, int totalNumber) {
        try ( LineNumberReader lnr = new LineNumberReader(new FileReader(inputFile));
              BufferedWriter myOutput = new BufferedWriter(new FileWriter(outputFile));) {
            String line;
            while ((line = lnr.readLine()) != null) {
                if (lnr.getLineNumber() < totalNumber) {
                    myOutput.write(line + System.lineSeparator());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void combineRandomSpmf(File inputFile, File outputFile, int totalNumber) {
        Set<Integer> numberSet = new HashSet<>();
        Random random = new Random();

        while(numberSet.size() < totalNumber) {
            int i = random.nextInt(887167);
            numberSet.add(i);
        }

        String line;
        try ( LineNumberReader lnr = new LineNumberReader(new FileReader(inputFile));
              BufferedWriter myOutput = new BufferedWriter(new FileWriter(outputFile));) {
            while ((line = lnr.readLine()) != null) {
                if (numberSet.contains(lnr.getLineNumber())) {
                    myOutput.write(line + System.lineSeparator());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 该方法输入的目录最后必须以/结尾
     * @param inputDirectory 输入目录
     * @param outputDirectory 输出目录
     * @param segmentationNumber 每个合并片段的长度
     */
    public static void combineAllSpmf(File inputDirectory, String outputDirectory, int segmentationNumber) {
        int count = 1;
        int round = 1;
        try {
            BufferedWriter myOutput = new BufferedWriter(new FileWriter(outputDirectory
                    + String.valueOf(round) + ".txt"));
            File[] files = inputDirectory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (count > segmentationNumber) {
                        count = 1;
                        round++;
                        myOutput.flush();
                        myOutput.close();
                        myOutput = new BufferedWriter(new FileWriter(outputDirectory
                                + String.valueOf(round) + ".txt"));
                        System.out.println("完成" + round + "轮");
                    } else {
                        count++;
                    }
                    BufferedReader myInput = new BufferedReader(new FileReader(file));
                    String thisLine = null;
                    while ((thisLine = myInput.readLine()) != null) {
                        myOutput.write(thisLine + System.lineSeparator());
                    }
                    myInput.close();
                }
            }
            myOutput.flush();
            myOutput.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public static void main(String[] args) {
//        S2LatLng start = S2LatLng.fromDegrees(39.5, 116);
//        S2LatLng end = S2LatLng.fromDegrees(40.3, 116.8);
//        S2LatLng startTop = S2LatLng.fromDegrees(40.3, 116);
//        S2LatLng endDown = S2LatLng.fromDegrees(39.5, 116.8);

//        String trip = "F:/论文/毕业论文/中间结果/trip/";
//        String merge = "F:/论文/毕业论文/中间结果/merge/";
//        String grid = "F:/论文/毕业论文/中间结果/grid/";
//        String spmf = "F:/论文/毕业论文/spmfData/vehicle/";
        String modGrid = "F:/论文/毕业论文/中间结果/modGrid/";
        String modGrid20 = "F:/论文/毕业论文/中间结果/modGrid20/";
        String modGrid5 = "F:/论文/毕业论文/中间结果/modGrid5/";
        String modVehicle = "F:/论文/毕业论文/spmfData/modVehicle/";
        String modVehicle20 = "F:/论文/毕业论文/spmfData/modVehicle20/";
        String modVehicle5 = "F:/论文/毕业论文/spmfData/modVehicle5/";

        String result = "F:/论文/毕业论文/spmfData/hist/";
//  ------ deprecated frame
//        File inputDirectory = new File("F:/论文/毕业论文/实验数据集/release/taxi_log_2008_by_id/");
//        File[] files = inputDirectory.listFiles();
//        if (files != null) {
//            for (File file : files) {
//                DriveTransformer.generateTrip(file, new File(trip + file.getName()));
//            }
//        }
//
//        File inputTripDirectory = new File(trip);
//        File[] tripFiles = inputTripDirectory.listFiles();
//        if (tripFiles != null) {
//            for (File file : tripFiles) {
//                DriveTransformer.mergeSinglePointTrip(file, new File(merge + file.getName()));
//            }
//        }
//
//        File inputMergeDirectory = new File(merge);
//        File[] mergeFiles = inputMergeDirectory.listFiles();
//        if (mergeFiles != null) {
//            for (File file : mergeFiles) {
//                DriveTransformer.gridFormation(file, new File(grid + file.getName()));
//            }
//        }
//
//        File inputGridDirectory = new File(grid);
//        File[] gridFiles = inputGridDirectory.listFiles();
//        if (gridFiles != null) {
//            for (File file : gridFiles) {
//                DriveTransformer.tDrive2Spmf(file, new File(spmf + file.getName()));
//            }
//        }

//
//        String test = "78114 -1 78115 -1 78114 -1 -2";
//        StringTokenizer tokenizer = new StringTokenizer(test);
//        String token;
//        while (tokenizer.hasMoreElements()) {
//            token = tokenizer.nextToken();
//            System.out.println(token);
//        }

//  ------ new framework
//        File inputDirectory = new File("F:/论文/毕业论文/实验数据集/release/taxi_log_2008_by_id/");
//        File[] files = inputDirectory.listFiles();
//        if (files != null) {
//            for (File file : files) {
//                DriveTransformer.modGridFormation(file, new File(modGrid + file.getName()));
//            }
//        }
//
//        File inputGridDirectory = new File(modGrid);
//        File[] gridFiles = inputGridDirectory.listFiles();
//        if (gridFiles != null) {
//            for (File file : gridFiles) {
//                int count = DriveTransformer.modTDriveSpmf(file,
//                        new File(modVehicle + file.getName()));
//
//            }
//        }
//        System.out.println(lengthCount);
//        System.out.println(symbolSet.size());
//        System.out.println(sequenceCount);

        //        try ( BufferedWriter resultOutput = new BufferedWriter(
//                new FileWriter(new File(result + "histSData.txt")))) {
//            File inputGridDirectory = new File(modGrid);
//            File[] gridFiles = inputGridDirectory.listFiles();
//            if (gridFiles != null) {
//                for (File file : gridFiles) {
//                    DriveTransformer.modTDriveSpmf(file,
//                            new File(modVehicle + file.getName()), resultOutput);
//                }
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

//        DriveTransformer.combineAllSpmf(new File(modVehicle),
//                "F:/论文/毕业论文/spmfData/100000spmf/",
//                1148);
//      626;507;380;236;122
//        DriveTransformer.combineSpmf(new File(modVehicle),
//                new File("F:/论文/毕业论文/spmfData/all100000.txt"),
//                10336);
//        DriveTransformer.combineRandomSpmf(new File("F:/论文/毕业论文/spmfData/all.txt"),
//                new File("F:/论文/毕业论文/spmfData/newAll50000.txt"), 50000);
//        DriveTransformer.cutSpmf(new File("F:/论文/毕业论文/spmfData/100000spmf/1.txt"),
//                new File("F:/论文/毕业论文/spmfData/100000spmf/cutData/90000.txt"), 90000);

        double lat = 39.92123;
        double lng = 116.51172;
        S2LatLng s2LatLng = S2LatLng.fromDegrees(lat, lng);
        S2CellId s2CellId = S2CellId.fromLatLng(s2LatLng).parent(14);
        int preLocation = s2CellId.getPartialKey();
        System.out.println(Long.toBinaryString(s2CellId.id()));
        System.out.println(preLocation);
    }

}
