package com.bom.zcloudbackend.operation.download.product;

import com.bom.zcloudbackend.common.util.PathUtil;
import com.bom.zcloudbackend.operation.download.Downloader;
import com.bom.zcloudbackend.operation.download.domain.DownloadFile;

import javax.servlet.http.HttpServletResponse;
import java.io.*;

public class LocalStorageDownloader extends Downloader {

    @Override
    public void download(HttpServletResponse response, DownloadFile downloadFile) {
        BufferedInputStream bis = null;
        byte[] buffer = new byte[1024];
        //设置文件路径
        File file = new File(PathUtil.getStaticPath() + downloadFile.getFileUrl());
        if (file.exists()) {

            FileInputStream fis = null;

            try {
                fis = new FileInputStream(file);
                bis = new BufferedInputStream(fis);
                OutputStream os = response.getOutputStream();
                int i = bis.read(buffer);
                while (i != -1) {
                    os.write(buffer, 0, i);
                    i = bis.read(buffer);
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (bis != null) {
                    try {
                        bis.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}