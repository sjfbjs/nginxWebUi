package com.cym.controller.adminPage;

import java.util.List;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import com.cym.config.InitConfig;
import com.cym.model.Http;
import com.cym.model.LogInfo;
import com.cym.service.HttpService;
import com.cym.service.SettingService;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;
import com.cym.utils.SnowFlakeUtils;

import cn.craccd.sqlHelper.bean.Sort;
import cn.craccd.sqlHelper.bean.Sort.Direction;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

@Controller
@RequestMapping("/adminPage/http")
public class HttpController extends BaseController {
	@Autowired
	HttpService httpService;
	@Autowired
	SettingService settingService;

	@RequestMapping("")
	public ModelAndView index(HttpSession httpSession, ModelAndView modelAndView) {
		List<Http> httpList = httpService.findAll(); 

		modelAndView.addObject("httpList", httpList);
		modelAndView.setViewName("/adminPage/http/index");
		return modelAndView;
	}

	@RequestMapping("addOver")
	@ResponseBody
	public JsonResult addOver(Http http) {
		if (StrUtil.isEmpty(http.getId())) {
			http.setSeq( SnowFlakeUtils.getId());
		}
		sqlHelper.insertOrUpdate(http);

		return renderSuccess();
	}

	@RequestMapping("addTemplate")
	@ResponseBody
	public JsonResult addTemplate(String templateId) {
		httpService.addTemplate(templateId);
		
		return renderSuccess();
	}
	
	@RequestMapping("detail")
	@ResponseBody
	public JsonResult detail(String id) {
		return renderSuccess(sqlHelper.findById(id, Http.class));
	}

	@RequestMapping("del")
	@ResponseBody
	public JsonResult del(String id) {
		sqlHelper.deleteById(id, Http.class);

		return renderSuccess();
	}

	@RequestMapping("addGiudeOver")
	@ResponseBody
	public JsonResult addGiudeOver(String json, Boolean logStatus, Boolean webSocket) {
		List<Http> https = JSONUtil.toList(JSONUtil.parseArray(json), Http.class);

		if (logStatus) {
//			Http http = new Http();
//			http.setName("log_format");
//			http.setValue("main escape=json '" + buildLogFormat() + "'");
//			http.setUnit("");
//			https.add(http);

			Http http = new Http();
			http.setName("access_log");
			http.setValue(InitConfig.home + "log/access.log");
			http.setUnit("");
			https.add(http);

		}

		if (webSocket) {
			Http http = new Http();
			http.setName("map");
			http.setValue("$http_upgrade $connection_upgrade {\r\n" + "    default upgrade;\r\n" + "    '' close;\r\n" + "}\r\n" + "");
			http.setUnit("");
			https.add(http);
		}

		httpService.setAll(https);

		return renderSuccess();
	}

//	private String buildLogFormat() {
//		LogInfo logInfo = new LogInfo();
//		logInfo.setRemoteAddr("$remote_addr");
//		logInfo.setRemoteUser("$remote_user");
//		logInfo.setTimeLocal("$time_local");
//		logInfo.setRequest("$request");
//		logInfo.setHttpHost("$http_host");
//		logInfo.setStatus("$status");
//		logInfo.setRequestLength("$request_length");
//		logInfo.setBodyBytesDent("$body_bytes_sent");
//		logInfo.setHttpReferer("$http_referer");
//		logInfo.setHttpUserAgent("$http_user_agent");
//		logInfo.setRequestTime("$request_time");
//		logInfo.setUpstreamResponseTime("$upstream_response_time");
//
//		return JSONUtil.toJsonStr(logInfo);
//	}

	@RequestMapping("setOrder")
	@ResponseBody
	public JsonResult setOrder(String id, Integer count) {
		httpService.setSeq(id, count);
		return renderSuccess();
	}
}
