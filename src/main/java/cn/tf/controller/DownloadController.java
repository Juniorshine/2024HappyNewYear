package cn.tf.controller;

import cn.tf.service.DownloadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RequestMapping
@RestController
public class DownloadController {

    private static final Logger log = LoggerFactory.getLogger(DownloadController.class);

    @Autowired
    private HttpServletRequest request;
    @Autowired
    private DownloadService downloadService;


    @RequestMapping("/download")
    public void download() {
        String URL = "https://medium.com";
        downloadService.downloadPDFFileFromURL(URL);
    }
}
