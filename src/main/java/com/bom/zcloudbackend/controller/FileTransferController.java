package com.bom.zcloudbackend.controller;

import com.bom.zcloudbackend.common.RespResult;
import com.bom.zcloudbackend.common.util.DateUtil;
import com.bom.zcloudbackend.common.util.FileUtil;
import com.bom.zcloudbackend.dto.DownloadFileDTO;
import com.bom.zcloudbackend.dto.EncUploadFileDTO;
import com.bom.zcloudbackend.dto.UploadFileDTO;
import com.bom.zcloudbackend.entity.File;
import com.bom.zcloudbackend.entity.Storage;
import com.bom.zcloudbackend.entity.User;
import com.bom.zcloudbackend.entity.UserFile;
import com.bom.zcloudbackend.service.FileService;
import com.bom.zcloudbackend.service.FileTransferService;
import com.bom.zcloudbackend.service.UserFileService;
import com.bom.zcloudbackend.service.UserService;
import com.bom.zcloudbackend.vo.UploadFileVO;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/fileTransfer")
public class FileTransferController {

    @Resource
    private UserService userService;

    @Resource
    private FileService fileService;

    @Resource
    private UserFileService userFileService;

    @Resource
    private FileTransferService fileTransferService;

    @ApiOperation(value = "极速上传", notes = "检验md5判断文件是否存在，存在则直接上传返回skipUpload=true,不存在则返回skipUpload=false再次调用该接口的post方法")
    @GetMapping("/uploadFile")
    public RespResult<UploadFileVO> uploadFileSpeed(UploadFileDTO uploadFileDTO, @RequestHeader("token") String token) {
        User sessionUser = userService.getUserByToken(token);
        if (sessionUser == null) {
            return RespResult.fail().message("未登录");
        }

        UploadFileVO uploadFileVO = new UploadFileVO();
        HashMap<String, Object> param = new HashMap<>();
        param.put("identifier", uploadFileDTO.getIdentifier());
        synchronized (FileTransferController.class) {
            List<File> list = fileService.listByMap(param);     //查找文件
            if (list != null && !list.isEmpty()) {
                //服务器已存在相同文件
                File file = list.get(0);    //唯一文件
                UserFile userFile = new UserFile();
                userFile.setFileId(file.getFileId());
                userFile.setUserId(sessionUser.getUserId());
                userFile.setFilePath(uploadFileDTO.getFilePath());
                String fileName = uploadFileDTO.getFileName();
                userFile.setFileName(fileName.substring(0, fileName.lastIndexOf(".")));
                userFile.setExtendName(FileUtil.getFileExtendName(fileName));
                userFile.setIsDir(0);
                userFile.setUploadTime(DateUtil.getCurrentTime());
                userFile.setDeleteTag(0);   //未删除
                userFileService.save(userFile);
                uploadFileVO.setSkipUpload(true);   //跳过上传
            } else {
                //需要上传,skipUpload=false
                uploadFileVO.setSkipUpload(false);
            }
        }
        return RespResult.success().data(uploadFileVO);
    }

    @ApiOperation(value = "上传文件夹", notes = "真正的文件上传接口")
    @PostMapping("/uploadFile")
    public RespResult<UploadFileVO> uploadFile(HttpServletRequest request, UploadFileDTO uploadFileDto,
        @RequestHeader("token") String token) {

        User sessionUser = userService.getUserByToken(token);
        if (sessionUser == null) {
            return RespResult.fail().message("未登录");
        }

        fileTransferService.uploadFile(request, uploadFileDto, sessionUser.getUserId());
        UploadFileVO uploadFileVo = new UploadFileVO();
        return RespResult.success().data(uploadFileVo);     //只返回了成功上传信息

    }

    @ApiOperation(value = "下载文件")
    @RequestMapping("/downloadFile")
    public void downloadFile(HttpServletResponse response, DownloadFileDTO downloadFileDTO) {
        fileTransferService.downloadFile(response, downloadFileDTO);
    }

    @ApiOperation("获取存储信息")
    @GetMapping("/getStorage")
    public RespResult<Long> getStorage(@RequestHeader("token") String token) {
        User sessionUser = userService.getUserByToken(token);
        Storage storage = new Storage();
        Long size = fileTransferService.selectStorageSizeByUserId(sessionUser.getUserId());
        return RespResult.success().data(size);
    }

    @ApiOperation("上传加密文件")
    @RequestMapping("/uploadEncFile")
    public RespResult uploadEncFile(HttpServletRequest request, EncUploadFileDTO encUploadFileDTO,
        @RequestHeader("token") String token) {
        User sessionUser = userService.getUserByToken(token);
        if (sessionUser == null) {
            return RespResult.fail().message("未登录");
        }
        fileTransferService.uploadEncFile(request, encUploadFileDTO, sessionUser.getUserId());
        return RespResult.success();
    }

}
