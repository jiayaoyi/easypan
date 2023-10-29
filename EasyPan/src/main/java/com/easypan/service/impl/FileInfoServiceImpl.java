package com.easypan.service.impl;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import com.easypan.component.RedisComponent;
import com.easypan.entity.config.AppConfig;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.dto.UploadResultDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.easypan.entity.enums.*;
import com.easypan.entity.po.UserInfo;
import com.easypan.entity.query.UserInfoQuery;
import com.easypan.exception.BusinessException;
import com.easypan.mappers.UserInfoMapper;
import com.easypan.utils.DateUtil;
import com.easypan.utils.ProcessUtils;
import com.easypan.utils.ScaleFilter;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.easypan.entity.query.FileInfoQuery;
import com.easypan.entity.po.FileInfo;
import com.easypan.entity.vo.PaginationResultVO;
import com.easypan.entity.query.SimplePage;
import com.easypan.mappers.FileInfoMapper;
import com.easypan.service.FileInfoService;
import com.easypan.utils.StringTools;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;


/**
 * 文件信息 业务接口实现
 */
@Service("fileInfoService")
public class FileInfoServiceImpl implements FileInfoService {

    private static final Logger logger = LoggerFactory.getLogger(FileInfoServiceImpl.class);
    @Resource
    private FileInfoMapper<FileInfo, FileInfoQuery> fileInfoMapper;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

    @Resource
    private AppConfig appConfig;

    @Resource
    @Lazy
    private FileInfoServiceImpl fileInfoService;
    /**
     * 根据条件查询列表
     */
    @Override
    public List<FileInfo> findListByParam(FileInfoQuery param) {
        return this.fileInfoMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(FileInfoQuery param) {
        return this.fileInfoMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<FileInfo> findListByPage(FileInfoQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<FileInfo> list = this.findListByParam(param);
        PaginationResultVO<FileInfo> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(FileInfo bean) {
        return this.fileInfoMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<FileInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.fileInfoMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<FileInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.fileInfoMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    @Override
    public Integer updateByParam(FileInfo bean, FileInfoQuery param) {
        StringTools.checkParam(param);
        return this.fileInfoMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    @Override
    public Integer deleteByParam(FileInfoQuery param) {
        StringTools.checkParam(param);
        return this.fileInfoMapper.deleteByParam(param);
    }

    /**
     * 根据FileIdAndUserId获取对象
     */
    @Override
    public FileInfo getFileInfoByFileIdAndUserId(String fileId, String userId) {
        return this.fileInfoMapper.selectByFileIdAndUserId(fileId, userId);
    }

    /**
     * 根据FileIdAndUserId修改
     */
    @Override
    public Integer updateFileInfoByFileIdAndUserId(FileInfo bean, String fileId, String userId) {
        return this.fileInfoMapper.updateByFileIdAndUserId(bean, fileId, userId);
    }

    /**
     * 根据FileIdAndUserId删除
     */
    @Override
    public Integer deleteFileInfoByFileIdAndUserId(String fileId, String userId) {
        return this.fileInfoMapper.deleteByFileIdAndUserId(fileId, userId);
    }

    /**
     * 获取用户已使用空间
     */
    @Override
    public Long getUsedSpace(String userId) {
        return this.fileInfoMapper.selectUserSpace(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadResultDto uploadFile(SessionWebUserDto webUserDto, String fileId, MultipartFile file, String fileName, String filePid, String fileMd5, Integer chunkIndex, Integer chunks) {
        UploadResultDto uploadResultDto = new UploadResultDto();
        Boolean uploadSuccess = true;
        File tempFileFolder = null;

        try {
            if (StringTools.isEmpty(fileId)) {
                fileId = StringTools.getRandomNumber(Constants.LENGTH_10);
            }
            uploadResultDto.setFileId(fileId);
            Date currentDate = new Date();
            UserSpaceDto userSpaceDto = redisComponent.getUserSpaceUse(webUserDto.getUserId());

            if (chunkIndex == 0) {
                FileInfoQuery fileInfoQuery = new FileInfoQuery();
                fileInfoQuery.setFileMd5(fileMd5);
                fileInfoQuery.setSimplePage(new SimplePage(0, 1));
                fileInfoQuery.setStatus(FileStatusEnums.USING.getStatus());
                List<FileInfo> dbFileInfoList = this.fileInfoMapper.selectList(fileInfoQuery);
                if (!dbFileInfoList.isEmpty()) {
                    FileInfo dbFile = dbFileInfoList.get(0);
                    //判断文件大小
                    if (dbFile.getFileSize() + userSpaceDto.getUseSpace() > userSpaceDto.getTotalSpace()) {
                        throw new BusinessException(ResponseCodeEnum.CODE_904);
                    }
                    dbFile.setFileId(fileId);
                    dbFile.setFilePid(filePid);
                    dbFile.setUserId(webUserDto.getUserId());
                    dbFile.setCreateTime(currentDate);
                    dbFile.setLastUpdateTime(currentDate);
                    dbFile.setStatus(FileStatusEnums.USING.getStatus());
                    dbFile.setDelFlag(FileStatusEnums.USING.getStatus());
                    dbFile.setFileMd5(fileMd5);
                    fileName = autoRename(filePid, webUserDto.getUserId(), fileName);
                    dbFile.setFileName(fileName);
                    this.fileInfoMapper.insert(dbFile);
                    uploadResultDto.setStatus(UploadStatusEnums.UPLOAD_SECONDS.getCode());
                    updateUseSpace(webUserDto, dbFile.getFileSize());
                    return uploadResultDto;
                }
            }
            //判断磁盘空间
            Long currentTempSize = redisComponent.getFileTempSize(webUserDto.getUserId(), fileId);
            if (file.getSize() + currentTempSize > userSpaceDto.getTotalSpace()) {
                throw new BusinessException(ResponseCodeEnum.CODE_904);
            }
            //暂存临时目录
            String tempFoldername = appConfig.getProjectFolder() + Constants.FILE_FOLDER_TEMP;
            String currentUserFolderName = webUserDto.getUserId() + fileId;
            tempFileFolder = new File(tempFoldername + currentUserFolderName);
            if (!tempFileFolder.exists()) {
                tempFileFolder.mkdirs();
            }
            File newFile = new File(tempFileFolder.getPath() + "/" + chunkIndex);
            file.transferTo(newFile);
            if (chunkIndex < chunks - 1) {
                uploadResultDto.setStatus(UploadStatusEnums.UPLOADING.getCode());
                return uploadResultDto;
            }

            redisComponent.saveFileTempSize(webUserDto.getUserId(), fileId, file.getSize());
            //最后一个分片上传完毕，异步合并，记录数据库
            String month = DateUtil.format(currentDate, DateTimePatternEnum.YYYYMM.getPattern());
            String fileSuffix = StringTools.getFileNameSuffix(fileName);
            //真实文件名
            String realFileName = currentUserFolderName + fileSuffix;
            FileTypeEnums fileTypeEnum = FileTypeEnums.getFileTypeBySuffix(fileSuffix);
            fileName = autoRename(filePid, webUserDto.getUserId(), fileName);
            FileInfo fileInfo = new FileInfo();
            fileInfo.setFileId(fileId);
            fileInfo.setUserId(webUserDto.getUserId());
            fileInfo.setFileMd5(fileMd5);
            fileInfo.setFileName(fileName);
            fileInfo.setFilePath(month + "/" + realFileName);
            fileInfo.setFilePid(filePid);
            fileInfo.setCreateTime(currentDate);
            fileInfo.setLastUpdateTime(currentDate);
            fileInfo.setFileCategory(fileTypeEnum.getCategory().getCategory());
            fileInfo.setFileType(fileTypeEnum.getType());
            fileInfo.setStatus(FileStatusEnums.TRANSFER.getStatus());
            fileInfo.setFolderType(FileFolderTypeEnums.FILE.getType());
            fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
            this.fileInfoMapper.insert(fileInfo);
            Long totalSize = redisComponent.getFileTempSize(webUserDto.getUserId(), fileId);
            updateUseSpace(webUserDto,totalSize);
            uploadResultDto.setStatus(UploadStatusEnums.UPLOAD_FINISH.getCode());

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    transferFile(fileInfo.getFileId(),webUserDto );
                }
            });
            return uploadResultDto;
        } catch (BusinessException e) {
            logger.error("文件上传失败");
            uploadSuccess = false;
            throw e;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (!uploadSuccess && tempFileFolder != null) {
                try {
                    FileUtils.deleteDirectory(tempFileFolder);
                } catch (IOException e) {
                    logger.error("删除临时文件失败",e);
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo newFolder(String filePid, String userId, String folderName) {
        checkFileName(filePid, userId, folderName, FileFolderTypeEnums.FOLDER.getType());
        Date curDate = new Date();
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileId(StringTools.getRandomString(Constants.LENGTH_10));
        fileInfo.setUserId(userId);
        fileInfo.setFilePid(filePid);
        fileInfo.setFileName(folderName);
        fileInfo.setFolderType(FileFolderTypeEnums.FOLDER.getType());
        fileInfo.setCreateTime(curDate);
        fileInfo.setLastUpdateTime(curDate);
        fileInfo.setStatus(FileStatusEnums.USING.getStatus());
        fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
        this.fileInfoMapper.insert(fileInfo);

        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setFilePid(filePid);
        fileInfoQuery.setUserId(userId);
        fileInfoQuery.setFileName(folderName);
        fileInfoQuery.setFolderType(FileFolderTypeEnums.FOLDER.getType());
        fileInfoQuery.setDelFlag(FileDelFlagEnums.USING.getFlag());
        Integer count = this.fileInfoMapper.selectCount(fileInfoQuery);
        if (count > 1) {
            throw new BusinessException("文件夹" + folderName + "已经存在");
        }
        fileInfo.setFileName(folderName);
        fileInfo.setLastUpdateTime(curDate);
        return fileInfo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo rename(String fileId, String fileName, String userId) {
        FileInfo fileInfo = this.fileInfoMapper.selectByFileIdAndUserId(fileId,userId);
        if (fileInfo != null) {
            String filePid = fileInfo.getFilePid();
            checkFileName(filePid,userId,fileName, fileInfo.getFolderType());
            if (FileFolderTypeEnums.FILE.getType().equals(fileInfo.getFolderType())) {
                fileName = fileName + StringTools.getFileNameSuffix(fileInfo.getFileName());
            }
            Date curDate = new Date();
            FileInfo dbInfo = new FileInfo();
            dbInfo.setFileName(fileName);
            dbInfo.setLastUpdateTime(curDate);
            this.fileInfoMapper.updateByFileIdAndUserId(dbInfo,fileId,userId);
            FileInfoQuery fileInfoQuery = new FileInfoQuery();
            fileInfoQuery.setFilePid(filePid);
            fileInfoQuery.setUserId(userId);
            fileInfoQuery.setFileName(fileName);
            Integer count = this.fileInfoMapper.selectCount(fileInfoQuery);
            if (count > 1) {
                throw new BusinessException("文件名" + fileName + "已经存在 ");
            }
            fileInfo.setLastUpdateTime(curDate);
            fileInfo.setFileName(fileName);
            return fileInfo;
        } else {
            throw new BusinessException("文件不存在");
        }

    }

    @Override
    public void changeFolder(String fileIds, String filePid, String userId) {
        if (fileIds.equals(filePid)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (Constants.ZERO_STR.equals(filePid)) {
            FileInfo fileInfo = this.fileInfoMapper.selectByFileIdAndUserId(filePid,userId);
            if (fileInfo == null || !FileDelFlagEnums.USING.equals(fileInfo.getDelFlag())) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
        }
        String[] fileIdArray = fileIds.split(",");
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setFilePid(filePid);
        fileInfoQuery.setUserId(userId);
        List<FileInfo> dbFileInfos = this.fileInfoMapper.selectList(fileInfoQuery);

        Map<String,FileInfo> map = dbFileInfos.stream()
                .collect(Collectors
                        .toMap(FileInfo::getFileName,
                                Function.identity(),
                                (data1,data2) -> data2));
        fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setUserId(userId);
        fileInfoQuery.setFileIdArray(fileIdArray);
        List<FileInfo> selectedFileList = this.findListByParam(fileInfoQuery);
        for (FileInfo item : selectedFileList) {
            FileInfo rootFileInfo = map.get(item.getFileName());
            FileInfo updateFileInfo = new FileInfo();
            if (rootFileInfo != null) {
                String fileName = StringTools.rename(item.getFileName());
                updateFileInfo.setFileName(fileName);
            }
            updateFileInfo.setFilePid(filePid);
            this.fileInfoMapper.updateByFileIdAndUserId(updateFileInfo,item.getFileId(),userId);
        }
    }

    private void checkFileName(String filePid, String userId, String fileName, Integer folderType) {
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setFolderType(folderType);
        fileInfoQuery.setFileName(fileName);
        fileInfoQuery.setFilePid(filePid);
        fileInfoQuery.setUserId(userId);
        fileInfoQuery.setDelFlag(FileDelFlagEnums.USING.getFlag());
        Integer count = this.fileInfoMapper.selectCount(fileInfoQuery);
        if (count > 0) {
            throw new BusinessException("此目录下已存在同名文件，请修改名称");
        }
    }

    private String autoRename(String filePid, String userId, String filename) {
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setUserId(userId);
        fileInfoQuery.setFileId(filePid);
        fileInfoQuery.setDelFlag(FileDelFlagEnums.USING.getFlag());
        fileInfoQuery.setFileName(filename);
        Integer count = this.fileInfoMapper.selectCount(fileInfoQuery);
        if (count > 0) {
            filename = StringTools.rename(filename);
        }
        return filename;
    }

    private void updateUseSpace(SessionWebUserDto sessionWebUserDto, Long useSpace) {
        Integer count = userInfoMapper.updateUserSpace(sessionWebUserDto.getUserId(), useSpace, null);
        if (count == 0) {
            throw new BusinessException(ResponseCodeEnum.CODE_904);
        }
        UserSpaceDto userSpaceDto = redisComponent.getUserSpaceUse(sessionWebUserDto.getUserId());
        userSpaceDto.setUseSpace(userSpaceDto.getUseSpace() + useSpace);
        redisComponent.saveUserSpaceUse(sessionWebUserDto.getUserId(), userSpaceDto);
    }

    @Async
    public void transferFile (String fileId , SessionWebUserDto webUserDto) {
        Boolean transferSuccess = true;
        String targetFilePath = null;
        String cover = null;
        FileTypeEnums fileTypeEnums = null;
        FileInfo fileInfo = this.fileInfoMapper.selectByFileIdAndUserId(fileId, webUserDto.getUserId());
        try {
            if (fileInfo == null || !FileStatusEnums.TRANSFER.getStatus().equals(fileInfo.getStatus())) {
                return;
            }
            String tempFolderName = appConfig.getProjectFolder() + Constants.FILE_FOLDER_TEMP;
            String currentFolderName = webUserDto.getUserId() + fileId;
            File fileFolder = new File(tempFolderName + currentFolderName);
            String fileSuffix = StringTools.getFileNameSuffix(fileInfo.getFileName());
            String fileMonth = DateUtil.format(fileInfo.getCreateTime(),DateTimePatternEnum.YYYYMM.getPattern());

            String targetFileName = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE;
            File targetFolder = new File (targetFileName + "/" + fileMonth);
            if (!targetFolder.exists()) {
                targetFolder.mkdirs();
            }
            String realFileName = currentFolderName + fileSuffix;
            targetFilePath = targetFolder.getPath() + "/" + realFileName;

            //合并文件
            union(fileFolder.getPath(), targetFilePath,fileInfo.getFileName(),true);

            // 视频文件切割
            fileTypeEnums = FileTypeEnums.getFileTypeBySuffix(fileSuffix);
            if (fileTypeEnums.equals(FileTypeEnums.VIDEO)) {
                cutFileVideo(fileId,targetFilePath);
                //视频生成缩略图
                cover = fileMonth + "/" + currentFolderName + Constants.IMAGE_PNG_SUFFIX;
                String coverPath = targetFileName + "/" + cover;
                ScaleFilter.createCover4Video(new File(targetFilePath),Constants.LENGTH_150,new File(coverPath));
            } else if (fileTypeEnums.equals(FileTypeEnums.IMAGE)) {
                cover = fileMonth + "/" + realFileName.replace(".","_.");
                String coverPath = targetFileName + cover;
                Boolean created = ScaleFilter.createThumbnailWidthFFmpeg(new File(targetFilePath),Constants.LENGTH_150,new File(coverPath),false);
                if (!created) {
                    FileUtils.copyFile(new File(targetFilePath),new File(coverPath));
                }
            }

        } catch (Exception e) {
            logger.error("文件转码失败，文件id:{},用户ID:{}",fileId,webUserDto.getUserId(),e);
            transferSuccess = false;
        } finally {
            FileInfo updateFileInfo = new FileInfo();
            File unionedFile = new File(targetFilePath);
            System.out.println(targetFilePath);
            updateFileInfo.setFileSize(unionedFile.length());
            updateFileInfo.setFileCover(cover);
            updateFileInfo.setStatus(transferSuccess?FileStatusEnums.USING.getStatus():FileStatusEnums.TRANSFER_FAIL.getStatus());
            fileInfoMapper.updateFileStatusWithOldStatus(fileId, webUserDto.getUserId(),updateFileInfo,FileStatusEnums.TRANSFER.getStatus());
            //TODO 设置封面
        }


    }

    public static void union(String dirPath, String toFilePath, String fileName, boolean delSource) throws BusinessException {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            throw new BusinessException("目录不存在");
        }
        File fileList[] = dir.listFiles();
        File targetFile = new File(toFilePath);
        RandomAccessFile writeFile = null;
        try {
            writeFile = new RandomAccessFile(targetFile, "rw");
            byte[] b = new byte[1024 * 10];
            for (int i = 0; i < fileList.length; i++) {
                int len = -1;
                //创建读块文件的对象
                File chunkFile = new File(dirPath + File.separator + i);
                RandomAccessFile readFile = null;
                try {
                    readFile = new RandomAccessFile(chunkFile, "r");
                    while ((len = readFile.read(b)) != -1) {
                        writeFile.write(b, 0, len);
                    }
                } catch (Exception e) {
                    logger.error("合并分片失败", e);
                    throw new BusinessException("合并文件失败");
                } finally {
                    readFile.close();
                }
            }
        } catch (Exception e) {
            logger.error("合并文件:{}失败", fileName, e);
            throw new BusinessException("合并文件" + fileName + "出错了");
        } finally {
            try {
                if (null != writeFile) {
                    writeFile.close();
                }
            } catch (IOException e) {
                logger.error("关闭流失败", e);
            }
            if (delSource) {
                if (dir.exists()) {
                    try {
                        FileUtils.deleteDirectory(dir);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void cutFileVideo (String fileId,String videoFilePath) {
        File tsFolder = new File(videoFilePath.substring(0,videoFilePath.lastIndexOf(".")));
        if (!tsFolder.exists()) {
            tsFolder.mkdirs();
        }
        final String CMD_TRANSFER_2TS = "ffmpeg -y -i %s  -vcodec copy -acodec copy -vbsf h264_mp4toannexb %s";
        final String CMD_CUT_TS = "ffmpeg -i %s -c copy -map 0 -f segment -segment_list %s -segment_time 30 %s/%s_%%4d.ts";

        String tsPath = tsFolder + Constants.TS_NAME;
        //生成TS
        String cmd = String.format(CMD_TRANSFER_2TS,videoFilePath,tsPath);
        ProcessUtils.executeCommand(cmd,false);
        //生成索引文件.m3u8和切片.ts
        cmd = String.format(CMD_CUT_TS, tsPath, tsFolder.getPath() + "/" + Constants.M3U8_NAME, tsFolder.getPath(), fileId);
        ProcessUtils.executeCommand(cmd,false);
        new File(tsPath).delete();

    }

}