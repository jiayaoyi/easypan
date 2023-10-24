package com.easypan.service.impl;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import com.easypan.component.RedisComponent;
import com.easypan.entity.config.AppConfig;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.dto.SysSettingsDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.easypan.entity.enums.UserStatusEnum;
import com.easypan.exception.BusinessException;
import com.easypan.service.EmailCodeService;
import com.easypan.utils.OKHttpUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.ibatis.reflection.ArrayUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.easypan.entity.enums.PageSize;
import com.easypan.entity.query.UserInfoQuery;
import com.easypan.entity.po.UserInfo;
import com.easypan.entity.vo.PaginationResultVO;
import com.easypan.entity.query.SimplePage;
import com.easypan.mappers.UserInfoMapper;
import com.easypan.service.UserInfoService;
import com.easypan.utils.StringTools;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


/**
 * 用户信息 业务接口实现
 */
@Service("userInfoService")
public class UserInfoServiceImpl implements UserInfoService {

	private static final Logger logger = LoggerFactory.getLogger(UserInfoServiceImpl.class);


	@Resource
	private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

	@Resource
	private EmailCodeService emailCodeService;

	@Resource
	private RedisComponent redisComponent;

	@Resource
	private AppConfig appConfig;

	@Resource
	private FileInfoServiceImpl fileInfoService;
	/**
	 * 根据条件查询列表
	 */
	@Override
	public List<UserInfo> findListByParam(UserInfoQuery param) {
		return this.userInfoMapper.selectList(param);
	}

	/**
	 * 根据条件查询列表
	 */
	@Override
	public Integer findCountByParam(UserInfoQuery param) {
		return this.userInfoMapper.selectCount(param);
	}

	/**
	 * 分页查询方法
	 */
	@Override
	public PaginationResultVO<UserInfo> findListByPage(UserInfoQuery param) {
		int count = this.findCountByParam(param);
		int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

		SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
		param.setSimplePage(page);
		List<UserInfo> list = this.findListByParam(param);
		PaginationResultVO<UserInfo> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
		return result;
	}

	/**
	 * 新增
	 */
	@Override
	public Integer add(UserInfo bean) {
		return this.userInfoMapper.insert(bean);
	}

	/**
	 * 批量新增
	 */
	@Override
	public Integer addBatch(List<UserInfo> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.userInfoMapper.insertBatch(listBean);
	}

	/**
	 * 批量新增或者修改
	 */
	@Override
	public Integer addOrUpdateBatch(List<UserInfo> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.userInfoMapper.insertOrUpdateBatch(listBean);
	}

	/**
	 * 多条件更新
	 */
	@Override
	public Integer updateByParam(UserInfo bean, UserInfoQuery param) {
		StringTools.checkParam(param);
		return this.userInfoMapper.updateByParam(bean, param);
	}

	/**
	 * 多条件删除
	 */
	@Override
	public Integer deleteByParam(UserInfoQuery param) {
		StringTools.checkParam(param);
		return this.userInfoMapper.deleteByParam(param);
	}

	/**
	 * 根据UserId获取对象
	 */
	@Override
	public UserInfo getUserInfoByUserId(String userId) {
		return this.userInfoMapper.selectByUserId(userId);
	}

	/**
	 * 根据UserId修改
	 */
	@Override
	public Integer updateUserInfoByUserId(UserInfo bean, String userId) {
		return this.userInfoMapper.updateByUserId(bean, userId);
	}

	/**
	 * 根据UserId删除
	 */
	@Override
	public Integer deleteUserInfoByUserId(String userId) {
		return this.userInfoMapper.deleteByUserId(userId);
	}

	/**
	 * 根据Email获取对象
	 */
	@Override
	public UserInfo getUserInfoByEmail(String email) {
		return this.userInfoMapper.selectByEmail(email);
	}

	/**
	 * 根据Email修改
	 */
	@Override
	public Integer updateUserInfoByEmail(UserInfo bean, String email) {
		return this.userInfoMapper.updateByEmail(bean, email);
	}

	/**
	 * 根据Email删除
	 */
	@Override
	public Integer deleteUserInfoByEmail(String email) {
		return this.userInfoMapper.deleteByEmail(email);
	}

	/**
	 * 根据NickName获取对象
	 */
	@Override
	public UserInfo getUserInfoByNickName(String nickName) {
		return this.userInfoMapper.selectByNickName(nickName);
	}

	/**
	 * 根据NickName修改
	 */
	@Override
	public Integer updateUserInfoByNickName(UserInfo bean, String nickName) {
		return this.userInfoMapper.updateByNickName(bean, nickName);
	}

	/**
	 * 根据NickName删除
	 */
	@Override
	public Integer deleteUserInfoByNickName(String nickName) {
		return this.userInfoMapper.deleteByNickName(nickName);
	}

	/**
	 * 根据QqOpenId获取对象
	 */
	@Override
	public UserInfo getUserInfoByQqOpenId(String qqOpenId) {
		return this.userInfoMapper.selectByQqOpenId(qqOpenId);
	}

	/**
	 * 根据QqOpenId修改
	 */
	@Override
	public Integer updateUserInfoByQqOpenId(UserInfo bean, String qqOpenId) {
		return this.userInfoMapper.updateByQqOpenId(bean, qqOpenId);
	}

	/**
	 * 根据QqOpenId删除
	 */
	@Override
	public Integer deleteUserInfoByQqOpenId(String qqOpenId) {
		return this.userInfoMapper.deleteByQqOpenId(qqOpenId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void register(String email, String nickName, String passWord, String emailCode) {
		UserInfo userInfo = userInfoMapper.selectByEmail(email);
		if (userInfo != null) {
			throw new BusinessException("邮箱已经被使用");
		}
		UserInfo userNickName = userInfoMapper.selectByNickName(nickName);
		if (userNickName != null) {
			throw new BusinessException("昵称已经被使用");
		}

		//校验邮箱验证码
		emailCodeService.checkCode(email,emailCode);

		String userId = StringTools.getRandomNumber(Constants.LENGTH_10);

		userInfo = new UserInfo();
		userInfo.setEmail(email);
		userInfo.setUserId(userId);
		userInfo.setPassword(StringTools.encodeMD5(passWord));
		userInfo.setNickName(nickName);
		userInfo.setStatus(UserStatusEnum.ENABLE.getStatus());
		userInfo.setUseSpace(0L);
		userInfo.setJoinTime(new Date());

		SysSettingsDto sysSettingsDto = redisComponent.getSysSettingsDto();

		userInfo.setTotalSpace(sysSettingsDto.getUserInitUseSpace()*Constants.MB);

		this.userInfoMapper.insert(userInfo);
	}

	@Override
	public SessionWebUserDto login(String email, String password) {
		UserInfo userInfo = userInfoMapper.selectByEmail(email);
		if (null == userInfo || !userInfo.getPassword().equals(password)) {
			throw new BusinessException("账号或密码错误");
		}

		if (userInfo.getStatus().equals(UserStatusEnum.DISABLE)) {
			throw new BusinessException("账号已被禁用");
		}
		UserInfo updateUserInfo = new UserInfo();
		updateUserInfo.setLastLoginTime(new Date());
		this.userInfoMapper.updateByUserId(updateUserInfo,userInfo.getUserId());

		SessionWebUserDto sessionWebUserDto = new SessionWebUserDto();
		sessionWebUserDto.setNickName(userInfo.getNickName());
		sessionWebUserDto.setUserId(userInfo.getUserId());
		if (ArrayUtils.contains(appConfig.getAdminEmails().split(","),email)) {
			sessionWebUserDto.setAdmin(true);
		} else {
			sessionWebUserDto.setAdmin(false);
		}
		UserSpaceDto userSpaceDto = new UserSpaceDto();
		userSpaceDto.setUseSpace(fileInfoService.getUsedSpace(userInfo.getUserId()));
		userSpaceDto.setTotalSpace(userInfo.getTotalSpace());
		redisComponent.saveUserSpaceUse(userInfo.getUserId(),userSpaceDto );
		return sessionWebUserDto;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void resetPwd(String email, String password, String emailCode) {
		UserInfo userInfo = userInfoMapper.selectByEmail(email);
		if (null == userInfo) {
			throw new BusinessException("邮箱不存在");
		}
		emailCodeService.checkCode(email,emailCode);
		UserInfo updateUserInfo = new UserInfo();
		updateUserInfo.setPassword(StringTools.encodeMD5(password));
		userInfoMapper.updateByEmail(updateUserInfo , userInfo.getEmail());

	}

	@Override
	public SessionWebUserDto qqLogin(String code) {

		return null;
	}

	private String getQQAccessToken(String code) {
		/**
		 * 返回结果是字符串 access_token=*&expires_in=7776000&refresh_token=* 返回错误 callback({UcWebConstants.VIEW_OBJ_RESULT_KEY:111,error_description:"error msg"})
		 */
		String accessToken = null;
		String url = null;
		try {
			url = String.format(appConfig.getQqUrlAccessToken(), appConfig.getQqAppId(), appConfig.getQqAppKey(), code, URLEncoder.encode(appConfig
					.getQqUrlRedirect(), "utf-8"));
		} catch (UnsupportedEncodingException e) {
			logger.error("encode失败");
		}
		String tokenResult = OKHttpUtils.getRequest(url);
		if (tokenResult == null || tokenResult.indexOf(Constants.VIEW_OBJ_RESULT_KEY) != -1) {
			logger.error("获取qqToken失败:{}", tokenResult);
			throw new BusinessException("获取qqToken失败");
		}
		String[] params = tokenResult.split("&");
		if (params != null && params.length > 0) {
			for (String p : params) {
				if (p.indexOf("access_token") != -1) {
					accessToken = p.split("=")[1];
					break;
				}
			}
		}
		return accessToken;
	}
}