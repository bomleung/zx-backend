package com.bom.zcloudbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bom.zcloudbackend.common.constant.FileConstant;
import com.bom.zcloudbackend.common.util.DateUtil;
import com.bom.zcloudbackend.entity.File;
import com.bom.zcloudbackend.entity.RecoveryFile;
import com.bom.zcloudbackend.entity.UserFile;
import com.bom.zcloudbackend.mapper.FileMapper;
import com.bom.zcloudbackend.mapper.RecoveryFileMapper;
import com.bom.zcloudbackend.mapper.UserFileMapper;
import com.bom.zcloudbackend.service.UserFileService;
import com.bom.zcloudbackend.vo.UserFileListVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author Frank Liang
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class UserFileServiceImpl extends ServiceImpl<UserFileMapper, UserFile> implements UserFileService {

    //TODO:改进线程池创建方式
    /**
     * 线程池
     */
    public static Executor executor = Executors.newFixedThreadPool(20);

    @Resource
    private UserFileMapper userFileMapper;

    @Resource
    private FileMapper fileMapper;

    @Resource
    private RecoveryFileMapper recoveryFileMapper;


    @Override
    public List<UserFileListVO> getUserFileByFilePath(String filePath, Long userId, Long currentPage, Long pageCount) {
        Long beginCount = (currentPage - 1) * pageCount;
        UserFile userfile = new UserFile();
        userfile.setUserId(userId);
        userfile.setFilePath(filePath);
        List<UserFileListVO> fileList = userFileMapper.userfileList(userfile, beginCount, pageCount);
        return fileList;
    }

    @Override
    public Map<String, Object> getUserFileByType(int fileType, Long currentPage, Long pageCount, Long userId) {
        Long beginCount = (currentPage - 1) * pageCount;
        List<UserFileListVO> fileList;
        Long total;
        if (fileType == FileConstant.OTHER_TYPE) {
            List<String> typeList = new ArrayList<>();
            typeList.addAll(Arrays.asList(FileConstant.DOC_FILE));
            typeList.addAll(Arrays.asList(FileConstant.IMG_FILE));
            typeList.addAll(Arrays.asList(FileConstant.VIDEO_FILE));
            typeList.addAll(Arrays.asList(FileConstant.MUSIC_FILE));

            fileList = userFileMapper.selectFileNotInExtendNames(typeList, beginCount, pageCount, userId);
            total = userFileMapper.selectCountNotInExtendNames(typeList, beginCount, pageCount, userId);
        } else {
            List<String> typeList = new ArrayList<>();
            if (fileType == FileConstant.IMAGE_TYPE) {
                typeList = Arrays.asList(FileConstant.IMG_FILE);
            } else if (fileType == FileConstant.DOC_TYPE) {
                typeList = Arrays.asList(FileConstant.DOC_FILE);
            } else if (fileType == FileConstant.VIDEO_TYPE) {
                typeList = Arrays.asList(FileConstant.VIDEO_FILE);
            } else if (fileType == FileConstant.MUSIC_TYPE) {
                typeList = Arrays.asList(FileConstant.MUSIC_FILE);
            }
            fileList = userFileMapper.selectFileByExtendName(typeList, beginCount, pageCount, userId);
            total = userFileMapper.selectCountByExtendName(typeList, beginCount, pageCount, userId);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("list", fileList);
        map.put("total", total);
        return map;
    }

    @Override
    public void deleteUserFile(Long userFileId, Long sessionUserId) {
        UserFile userFile = userFileMapper.selectById(userFileId);
        String uuid = UUID.randomUUID().toString();
        System.out.println(userFile);
        if (userFile.getIsDir() == 1) {
            LambdaUpdateWrapper<UserFile> userFileLambdaUpdateWrapper = new LambdaUpdateWrapper<>();
            userFileLambdaUpdateWrapper.set(UserFile::getDeleteTag, 1)
                .set(UserFile::getDeleteBatchNum, uuid)
                .set(UserFile::getDeleteTime, DateUtil.getCurrentTime())
                .eq(UserFile::getUserFileId, userFileId);
            userFileMapper.update(null, userFileLambdaUpdateWrapper);
            //在这里不删除userfile记录，在AsyncUtil中删除userfile记录
//            userFileMapper.deleteById(userFileId);

            String filePath = userFile.getFilePath() + userFile.getFileName() + "/";
            updateFileDeleteStateByFilePath(filePath, uuid, sessionUserId);

        } else {
            UserFile userFileTemp = userFileMapper.selectById(userFileId);
            File file = fileMapper.selectById(userFileTemp.getFileId());
            LambdaUpdateWrapper<UserFile> userFileLambdaUpdateWrapper = new LambdaUpdateWrapper<>();
            userFileLambdaUpdateWrapper.set(UserFile::getDeleteTag, 1)
                .set(UserFile::getDeleteTime, DateUtil.getCurrentTime())
                .set(UserFile::getDeleteBatchNum, uuid)
                .eq(UserFile::getUserFileId, userFileTemp.getUserFileId());
            userFileMapper.update(null, userFileLambdaUpdateWrapper);
//            userFileMapper.deleteById(userFileTemp.getUserFileId());

        }

        RecoveryFile recoveryFile = new RecoveryFile();
        recoveryFile.setUserFileId(userFileId);
        recoveryFile.setDeleteTime(DateUtil.getCurrentTime());
        recoveryFile.setDeleteBatchNum(uuid);
        recoveryFileMapper.insert(recoveryFile);

    }

    @Override
    public List<UserFile> selectFileTreeListLikeFilePath(String filePath, long userId) {

        filePath = filePath.replace("\\", "\\\\\\\\");
        filePath = filePath.replace("'", "\\'");
        filePath = filePath.replace("%", "\\%");
        filePath = filePath.replace("_", "\\_");

        LambdaQueryWrapper<UserFile> queryWrapper = new LambdaQueryWrapper<>();
        log.info("查询文件路径:" + filePath);

        queryWrapper.eq(UserFile::getUserId, userId)
            .likeRight(UserFile::getFilePath, filePath);
        return userFileMapper.selectList(queryWrapper);
    }

    /**
     * 删除目录时将该文件目录下的所有文件都放入回收站
     *
     * @param filePath
     * @param deleteBatchNum
     * @param userId
     */

    private void updateFileDeleteStateByFilePath(String filePath, String deleteBatchNum, Long userId) {
        executor.execute(() -> {
            List<UserFile> fileList = selectFileTreeListLikeFilePath(filePath, userId);
            for (int i = 0; i < fileList.size(); i++) {
                UserFile userFileTemp = fileList.get(i);
                //标记删除标志
                LambdaUpdateWrapper<UserFile> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.set(UserFile::getDeleteTag, 1)
                    .set(UserFile::getDeleteTime, DateUtil.getCurrentTime())
                    .set(UserFile::getDeleteBatchNum, deleteBatchNum)
                    .eq(UserFile::getUserFileId, userFileTemp.getUserFileId())
                    .eq(UserFile::getDeleteTag, 0);
                userFileMapper.update(null, updateWrapper);

            }
        });
    }

    @Override
    public List<UserFile> selectFilePathTreeByUserId(Long userId) {
        LambdaQueryWrapper<UserFile> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(UserFile::getUserId, userId)
            .eq(UserFile::getIsDir, 1)
            .eq(UserFile::getDeleteTag, 0);
        return userFileMapper.selectList(lambdaQueryWrapper);
    }

    @Override
    public void updateFilepathByFilepath(String oldfilePath, String newfilePath, String fileName, String extendName,
        Long userId) {
        if ("null".equals(extendName)) {
            extendName = null;
        }

        LambdaUpdateWrapper<UserFile> lambdaUpdateWrapper = new LambdaUpdateWrapper<UserFile>();
        lambdaUpdateWrapper.set(UserFile::getFilePath, newfilePath)
            .eq(UserFile::getFilePath, oldfilePath)
            .eq(UserFile::getFileName, fileName)
            .eq(UserFile::getUserId, userId);
        if (StringUtils.isNotEmpty(extendName)) {
            lambdaUpdateWrapper.eq(UserFile::getExtendName, extendName);
        } else {
            lambdaUpdateWrapper.isNull(UserFile::getExtendName);
        }
        userFileMapper.update(null, lambdaUpdateWrapper);
        //移动子目录
        oldfilePath = oldfilePath + fileName + "/";
        newfilePath = newfilePath + fileName + "/";

        oldfilePath = oldfilePath.replace("\\", "\\\\\\\\");
        oldfilePath = oldfilePath.replace("'", "\\'");
        oldfilePath = oldfilePath.replace("%", "\\%");
        oldfilePath = oldfilePath.replace("_", "\\_");

        //为null说明是目录，则需要移动子目录
        if (extendName == null) {
            userFileMapper.updateFilepathByFilepath(oldfilePath, newfilePath, userId);
        }
    }

    @Override
    public List<UserFile> selectUserFileByNameAndPath(String fileName, String filePath, Long userId) {
        LambdaQueryWrapper<UserFile> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFile::getFileName, fileName)
            .eq(UserFile::getFilePath, filePath)
            .eq(UserFile::getUserId, userId)
            .eq(UserFile::getDeleteTag, 0);
        return userFileMapper.selectList(queryWrapper);
    }

    @Override
    public void replaceUserFilePath(String filePath, String oldFilePath, Long userId) {
        userFileMapper.replaceFilePath(filePath, oldFilePath, userId);
    }

    @Override
    public List<UserFileListVO> searchFile(Long userId, String searchText, Long currentPage, Long pageCount) {
        Long beginCount = (currentPage - 1) * pageCount;
        List<UserFileListVO> list = userFileMapper.searchFile(userId, searchText, beginCount, pageCount);
        return list;
    }
}
