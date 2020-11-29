package net.gfeng;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将sony拍摄的素材重命名 拍摄日期-拍摄时间-序号.扩展名
 */
public class SonyAssetRenamer {

    public static String ffmpegPath ="C:\\app\\ffmpeg-4.2.1-win64-static\\bin\\ffmpeg";//ffmpeg安装目录
    public static String exifTool="c:\\app\\exiftool-11.84\\exiftool.exe";//exiftool可执行文件名
    public final static Logger logger = LoggerFactory.getLogger("root");

    public static void main(String[] args) {
        logger.info("program start!");
        String userDir = System.getProperty("user.dir");
        if(args.length>0){
            for(String arg:args){
                if(arg!=null && arg.contains("=")){
                    String[] argKeyPair=arg.split("=");
                    if (argKeyPair.length==2){
                        switch(argKeyPair[0]) {
                            case "ffmpeg":
                                ffmpegPath=argKeyPair[1].replaceAll("\\\\","/");
                                break;
                            case "exifTool":
                                exifTool=argKeyPair[1].replaceAll("\\\\","/");
                                break;
                            case "userDir":
                                userDir=argKeyPair[1].replaceAll("\\\\","/");
                                break;
                        }
                    }
                }
            }
        }
        logger.info("ffmpegPath="+ffmpegPath);
        logger.info("exifTool="+exifTool);
        logger.info("userDir="+userDir);
        File userDirFile = new File(userDir);
        for(File folder:userDirFile.listFiles()){
            if(folder.isDirectory()&&(folder.getName().startsWith("ARW")||folder.getName().startsWith("MP4"))){
                File[] files = folder.listFiles();
                for(File f:files){
                    if (f != null && !f.isDirectory() && f.getName().startsWith("C") && f.getName().endsWith(".MP4")) {
                        String newFileName = getVideoTime(f.getAbsolutePath(),"Asia/Shanghai");
                        fileRename(f,newFileName,".MP4");
                    }else if(f != null && !f.isDirectory() && f.getName().startsWith("C") && f.getName().endsWith("M01.XML")){
                        String newFileName = parseXml(f);
                        fileRename(f,newFileName,".XML");
                    }else if (f != null && !f.isDirectory() && f.getName().startsWith("DSC") && f.getName().endsWith(".ARW")) {
                        File xmpFile=new File(f.getAbsolutePath().substring(0,f.getAbsolutePath().length()-3)+"xmp");
                        String newFileName = getArwTime(f.getAbsolutePath());
                        fileRename(f,newFileName,".ARW");
                        if(xmpFile.exists()){
                            fileRename(xmpFile,newFileName,".xmp");
                        }
                    }
                }
            }

        }
    }

    private static String parseXml(File metaDataXmlFile) {
        String rtnStr=null;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance(); // 获取一个DocumentBuilderFactory的实例
        DocumentBuilder db = null; // 使用工厂生成一个DocumentBuilder
        try {
            db = dbf.newDocumentBuilder();
            Document doc = db.parse(metaDataXmlFile); // 使用dom解析xml文件
            NodeList urlList = doc.getElementsByTagName("CreationDate"); // 将所有节点名为product的节点取出
            Element creationDateElement;
            for (int i = 0; i < urlList.getLength(); i++) // 循环处理对象
            {
                creationDateElement = (Element) urlList.item(i);
                String creationDateStr = creationDateElement.getAttribute("value");
                rtnStr=creationDateStr.replaceAll("-","")
                        .replaceAll("T","_")
                        .replaceAll("\\+08:00","")
                        .replaceAll(":","");
            }
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rtnStr;
    }

    /**
     * 重命名文件
     * @param f
     * @param newFileName
     * @param ext
     */
    private static void fileRename(File f, String newFileName, String ext) {
        String newName=null;
        if(!f.renameTo(new File(f.getParent()+"\\"+newFileName+ext))){
            int i=1;
            while(!f.renameTo(new File(f.getParent()+"\\"+newFileName+"-"+i+ext))){//若发生文件重名，则在文件名后加上数字
                i++;
                if(i>100){
                    logger.info(f.getAbsolutePath()+" rename error!!!");
                    break;
                }
            }
            newName=newFileName+"-"+i+ext;
        }else{
            newName=newFileName+ext;
        }
        logger.info("rename:"+f.getName()+"->"+newName);
    }

    /**
     * 获取视频时间信息
     */
    private static String getVideoTime(String videoPath,String timeZoneStr) {
        String rtnStr=null;
        List<String> commands = new ArrayList<String>();
        commands.add(ffmpegPath);
        commands.add("-i");
        commands.add(videoPath);
        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command(commands);
            Process p = builder.start();

            //从输入流中读取视频信息
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            StringBuilder stringBuilder = new StringBuilder();
            String line = "";
            while ((line = br.readLine()) != null) {
                stringBuilder.append(line);
            }
            br.close();
            Pattern pattern=null;
            Matcher m=null;

            String regexCreationTime = "creation_time   : (\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{6}Z)";
            pattern = Pattern.compile(regexCreationTime);
            m = pattern.matcher(stringBuilder.toString());
            if (m.find()) {
                String utcTimeStr=m.group(1);

                SimpleDateFormat utcSdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'");
                utcSdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d=utcSdf.parse(utcTimeStr);

                SimpleDateFormat bjSdf = new SimpleDateFormat("yyyyMMdd'_'HHmmssSSSSSS");
                bjSdf.setTimeZone(TimeZone.getTimeZone(timeZoneStr));
                rtnStr = bjSdf.format(d);
                if(rtnStr.endsWith("000000"))rtnStr=rtnStr.substring(0,rtnStr.length()-6);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rtnStr;
    }

    /**
     * 调用exiftool工具获取arw文件的拍摄时间
     * @param arwFileName
     * @return
     */
    private static String getArwTime(String arwFileName) {
        String rtnStr=null;
        List<String> commands = new ArrayList<String>();
        commands.add(exifTool);
        commands.add(arwFileName);
        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command(commands);
            Process p = builder.start();

            //从输入流中读取视频信息
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder stringBuilder = new StringBuilder();
            String line = "";
            while ((line = br.readLine()) != null) {
                stringBuilder.append(line);
            }
            br.close();
            Pattern pattern=null;
            Matcher m=null;
            String regexCreationTime = "Sony Date Time                  : (\\d{4}):(\\d{2}):(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2})";
            pattern = Pattern.compile(regexCreationTime);
            m = pattern.matcher(stringBuilder.toString());
            if (m.find()) {
                String yyyy=m.group(1);
                String MM=m.group(2);
                String dd=m.group(3);
                String HH=m.group(4);
                String mm=m.group(5);
                String ss=m.group(6);
                rtnStr=yyyy+MM+dd+"_"+HH+mm+ss;
            }
            String regexSequenceNumber = "Sequence Number                 : (\\d*)";
            pattern = Pattern.compile(regexSequenceNumber);
            m = pattern.matcher(stringBuilder.toString());
            if (m.find()) {
                String seq=m.group(1);
                rtnStr=rtnStr+"_"+seq;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rtnStr;
    }

}