package com.jsh.erp.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.constants.ExceptionConstants;
import com.jsh.erp.datasource.entities.Depot;
import com.jsh.erp.datasource.entities.SystemConfig;
import com.jsh.erp.exception.BusinessRunTimeException;
import com.jsh.erp.service.depot.DepotService;
import com.jsh.erp.service.systemConfig.SystemConfigService;
import com.jsh.erp.service.user.UserService;
import com.jsh.erp.service.userBusiness.UserBusinessService;
import com.jsh.erp.utils.BaseResponseInfo;
import com.jsh.erp.utils.FileUtils;
import com.jsh.erp.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.List;

/**
 * Description
 * @Author: jishenghua
 * @Date: 2021-3-13 0:01
 */
@RestController
@RequestMapping(value = "/systemConfig")
public class SystemConfigController {
    private Logger logger = LoggerFactory.getLogger(SystemConfigController.class);

    @Resource
    private UserService userService;

    @Resource
    private DepotService depotService;

    @Resource
    private UserBusinessService userBusinessService;

    @Resource
    private SystemConfigService systemConfigService;

    @Value(value="${file.path}")
    private String filePath;

    @Value(value="${spring.servlet.multipart.max-file-size}")
    private Long maxFileSize;

    @Value(value="${spring.servlet.multipart.max-request-size}")
    private Long maxRequestSize;

    /**
     * ?????????????????????????????????
     * @param request
     * @return
     */
    @GetMapping(value = "/getCurrentInfo")
    public BaseResponseInfo getCurrentInfo(HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try{
            List<SystemConfig> list = systemConfigService.getSystemConfig();
            res.code = 200;
            if(list.size()>0) {
                res.data = list.get(0);
            }
        } catch(Exception e){
            e.printStackTrace();
            res.code = 500;
            res.data = "??????????????????";
        }
        return res;
    }

    /**
     * ????????????????????????
     * @param request
     * @return
     * @throws Exception
     */
    @GetMapping(value = "/fileSizeLimit")
    public BaseResponseInfo fileSizeLimit(HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try{
            Long limit = 0L;
            if(maxFileSize<maxRequestSize) {
                limit = maxFileSize;
            } else {
                limit = maxRequestSize;
            }
            res.code = 200;
            res.data = limit;
        } catch(Exception e){
            e.printStackTrace();
            res.code = 500;
            res.data = "??????????????????";
        }
        return res;
    }

    /**
     * ????????????????????????
     * @param request
     * @param response
     * @return
     */
    @PostMapping(value = "/upload")
    public BaseResponseInfo upload(HttpServletRequest request, HttpServletResponse response) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            String savePath = "";
            String bizPath = request.getParameter("biz");
            MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
            MultipartFile file = multipartRequest.getFile("file");// ????????????????????????
            if(StringUtil.isEmpty(bizPath)){
                bizPath = "";
            }
            savePath = this.uploadLocal(file,bizPath);
            if(StringUtil.isNotEmpty(savePath)){
                res.code = 200;
                res.data = savePath;
            }else {
                res.code = 500;
                res.data = "???????????????";
            }
        } catch (Exception e) {
            e.printStackTrace();
            res.code = 500;
            res.data = "???????????????";
        }
        return res;
    }

    /**
     * ??????????????????
     * @param mf ??????
     * @param bizPath  ???????????????
     * @return
     */
    private String uploadLocal(MultipartFile mf,String bizPath){
        try {
            String ctxPath = filePath;
            String fileName = null;
            File file = new File(ctxPath + File.separator + bizPath + File.separator );
            if (!file.exists()) {
                file.mkdirs();// ?????????????????????
            }
            String orgName = mf.getOriginalFilename();// ???????????????
            orgName = FileUtils.getFileName(orgName);
            if(orgName.indexOf(".")!=-1){
                fileName = orgName.substring(0, orgName.lastIndexOf(".")) + "_" + System.currentTimeMillis() + orgName.substring(orgName.indexOf("."));
            }else{
                fileName = orgName+ "_" + System.currentTimeMillis();
            }
            String savePath = file.getPath() + File.separator + fileName;
            File savefile = new File(savePath);
            FileCopyUtils.copy(mf.getBytes(), savefile);
            String dbpath = null;
            if(StringUtil.isNotEmpty(bizPath)){
                dbpath = bizPath + File.separator + fileName;
            }else{
                dbpath = fileName;
            }
            if (dbpath.contains("\\")) {
                dbpath = dbpath.replace("\\", "/");
            }
            return dbpath;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return "";
    }

    /**
     * ????????????&????????????
     * ???????????????http://localhost:8080/common/static/{financial/afsdfasdfasdf_1547866868179.txt}
     *
     * @param request
     * @param response
     */
    @GetMapping(value = "/static/**")
    public void view(HttpServletRequest request, HttpServletResponse response) {
        // ISO-8859-1 ==> UTF-8 ??????????????????
        String imgPath = extractPathFromPattern(request);
        if(StringUtil.isEmpty(imgPath) || imgPath=="null"){
            return;
        }
        // ???????????????
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            imgPath = imgPath.replace("..", "");
            if (imgPath.endsWith(",")) {
                imgPath = imgPath.substring(0, imgPath.length() - 1);
            }
            String fileUrl = filePath + File.separator + imgPath;
            File file = new File(fileUrl);
            if(!file.exists()){
                response.setStatus(404);
                throw new RuntimeException("???????????????..");
            }
            response.setContentType("application/force-download");// ???????????????????????????
            response.addHeader("Content-Disposition", "attachment;fileName=" + new String(file.getName().getBytes("UTF-8"),"iso-8859-1"));
            inputStream = new BufferedInputStream(new FileInputStream(fileUrl));
            outputStream = response.getOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = inputStream.read(buf)) > 0) {
                outputStream.write(buf, 0, len);
            }
            response.flushBuffer();
        } catch (IOException e) {
            logger.error("??????????????????" + e.getMessage());
            response.setStatus(404);
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }

    /**
     *  ?????????URL???????????????????????????????????????
     *  ????????????????????????URL????????????????????????????????????/?????????????????????????????????
     * @param request
     * @return
     */
    private static String extractPathFromPattern(final HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return new AntPathMatcher().extractPathWithinPattern(bestMatchPattern, path);
    }
}
