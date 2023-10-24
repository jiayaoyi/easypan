package com.easypan.controller;

import com.easypan.annotation.GlobalInterceptor;
import com.easypan.entity.config.AppConfig;
import com.easypan.entity.constants.Constants;
import com.easypan.utils.StringTools;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;

/**
 * CommonFileController
 *
 * @author Jia Yaoyi
 * @date 2023/10/24
 */
public class CommonFileController extends ABaseController{
    @Resource
    private AppConfig appConfig;

    public void getImage(HttpServletResponse response, String imageFolder, String imageName) {
        if (StringTools.isEmpty(imageFolder) || StringUtils.isBlank(imageName)) {
            return;
        }
        String imageSuffix = StringTools.getFileNameSuffix(imageName);
        String filePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + imageFolder + "/" + imageName;
        imageSuffix = imageSuffix.replace(".", "");
        String contentType = "image/" + imageSuffix;
        response.setContentType(contentType);
        response.setHeader("Cache-Control", "max-age=2592000");
        readFile(response, filePath);
    }
}
