package com.bom.zcloudbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bom.zcloudbackend.entity.UserFile;
import com.bom.zcloudbackend.vo.UserFileListVO;

import java.util.List;
import java.util.Map;

public interface UserFileService extends IService<UserFile> {

    List<UserFileListVO> getUserFileByFilePath(String filePath,Long userId,Long currentPage,Long pageCount);

    Map<String, Object> getUserFileByType(int fileType, Long currentPage, Long pageCount, Long userId);
}
