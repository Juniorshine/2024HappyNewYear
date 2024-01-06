package cn.tf.service;

import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DownloadService {
    private static final Logger log = LoggerFactory.getLogger(DownloadService.class);

    public void downloadPDFFileFromURL(String url, OutputStream outputStream){
        List<File> pdfList = new ArrayList();
        String id = UUID.randomUUID().toString();
        String zipFileName = "/home/xin/files/" + id + "/" + id + ".zip";
        File file = new File(zipFileName);
        // 获取文件的父目录
        File parentDir = file.getParentFile();

        // 检查父目录是否存在，如果不存在则创建
        if (!parentDir.exists()) {
            boolean created = parentDir.mkdirs(); // 创建所有父目录
            if (created) {
                log.info("父目录已创建");
            } else {
                log.info("无法创建父目录");
            }
        } else {
            log.info("父目录已存在");
        }
        // 获取页面信息
        try {
            Document doc = this.catchPageInfo(url);
            Elements articles  = doc.select("a h2");
            int i = 0;
            for (Element article  : articles ) {
                i++;
                String articleName = i + "-" + article.text();
                String href = article.parent().attr("href");
                if (href.isEmpty()) {
                    href = article.parent().parent().attr("href");
                }
                String articleURL = "";
                if (href.startsWith("/")) {
                    articleURL = url + href;
                } else {
                    articleURL = href;
                }
                log.info(i + " " +  articleName + " article link: " + articleURL);
                File pdfFile = this.getArticlePDF(articleURL, articleName, id);
                pdfList.add(pdfFile);
                if (i >= 10) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.createZipFile(pdfList, outputStream);

    }

    private File getArticlePDF(String articleURL, String articleName, String id) {
        // 设置代理
        System.setProperty("socksProxyHost", "127.0.0.1");
        System.setProperty("socksProxyPort", "10808");
        Translate translate = TranslateOptions.newBuilder().setApiKey("AIzaSyDkWQR4cMd0KdVw9RC-NiemPTbHqoebpeg").build().getService();

        String filePath = "/home/xin/files/" + id + "/" + articleName + ".pdf";
        try {
            Document doc = this.catchPageInfo(articleURL);
            Elements paragraphs = doc.select(".pw-post-body-paragraph");

            try (PDDocument document = new PDDocument()) {
                PDType0Font font = PDType0Font.load(document, new File("msyh.ttf"));
                PDPage page = new PDPage();
                document.addPage(page);

                float margin = 50; // 边距
                float width = page.getMediaBox().getWidth() - 2 * margin;
                float startX = margin;
                float startY = page.getMediaBox().getHeight() - margin;
                float lineHeight = 10; // 行间距
                float paragraphSpacing = 15; // 段落间距

                float topMargin = margin; // 上边距
                float bottomMargin = margin; // 下边距

                int linesWritten = 0;
                PDPageContentStream contentStream = new PDPageContentStream(document, page);
                contentStream.setFont(font, 8);

                for (Element paragraph : paragraphs) {
                    String text = paragraph.text();
//                    Translation translation = translate.translate(text, Translate.TranslateOption.targetLanguage("zh"));
//                    text = text + "\n" + translation.getTranslatedText();

                    String[] words = text.split(" ");
                    StringBuilder line = new StringBuilder();

                    for (String word : words) {
                        float wordWidth = font.getStringWidth(line.toString() + " " + word) / 1000 * 8;

                        if (wordWidth > width) {
                            if (startY - (linesWritten * lineHeight) <= bottomMargin) {
                                contentStream.close();

                                page = new PDPage();
                                document.addPage(page);

                                contentStream = new PDPageContentStream(document, page);
                                contentStream.setFont(font, 8);
                                contentStream.moveTo(margin, page.getMediaBox().getHeight() - topMargin);

                                startY = page.getMediaBox().getHeight() - topMargin;
                                linesWritten = 0;
                            }

                            contentStream.beginText();
                            contentStream.newLineAtOffset(startX, startY - (linesWritten * lineHeight));
                            contentStream.showText(line.toString());
                            contentStream.endText();
                            linesWritten++;

                            line = new StringBuilder(word);
                        } else {
                            if (line.length() > 0) {
                                line.append(" ");
                            }
                            line.append(word);
                        }
                    }

                    contentStream.beginText();
                    contentStream.newLineAtOffset(startX, startY - (linesWritten * lineHeight));
                    contentStream.showText(line.toString());
                    contentStream.endText();
                    linesWritten++;

                    startY -= paragraphSpacing + lineHeight;

                    if (startY <= bottomMargin) {
                        contentStream.close();

                        page = new PDPage();
                        document.addPage(page);

                        contentStream = new PDPageContentStream(document, page);
                        contentStream.setFont(font, 8);
                        contentStream.moveTo(margin, page.getMediaBox().getHeight() - topMargin);

                        startY = page.getMediaBox().getHeight() - topMargin;
                        linesWritten = 0;
                    }
                }

                contentStream.close();

                document.save(filePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            log.info("PDF 文件生成成功！");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new File(filePath);
    }

    private Document catchPageInfo(String url) throws IOException {
        // 创建一个代理对象并设置为 SOCKS 代理,国内需要翻墙
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 10808));
        // 使用代理连接网站
        Document doc = Jsoup.connect(url).proxy(proxy).get();
        log.info("connected!");
        return doc;
    }

    public void createZipFile(List<File> pdfFiles, OutputStream outputStream) {
        byte[] buffer = new byte[1024];

        try {
            ZipOutputStream zos = new ZipOutputStream(outputStream);

            for (File pdfFile : pdfFiles) {
                FileInputStream fis = new FileInputStream(pdfFile);
                zos.putNextEntry(new ZipEntry(pdfFile.getName()));

                int length;
                while ((length = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, length);
                }

                zos.closeEntry();
                fis.close();
            }

            zos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}