package com.cym.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import cn.hutool.core.util.ReUtil;
import cn.hutool.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cym.model.Location;
import com.cym.model.Param;
import com.cym.model.Server;
import com.cym.utils.SnowFlakeUtils;
import com.github.odiszapc.nginxparser.NgxBlock;
import com.github.odiszapc.nginxparser.NgxConfig;
import com.github.odiszapc.nginxparser.NgxEntry;
import com.github.odiszapc.nginxparser.NgxParam;

import cn.craccd.sqlHelper.bean.Page;
import cn.craccd.sqlHelper.bean.Sort;
import cn.craccd.sqlHelper.bean.Sort.Direction;
import cn.craccd.sqlHelper.utils.ConditionAndWrapper;
import cn.craccd.sqlHelper.utils.ConditionOrWrapper;
import cn.craccd.sqlHelper.utils.SqlHelper;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

@Service
public class ServerService {
    private final static Logger log = LoggerFactory.getLogger(ServerService.class);

    @Autowired
    SqlHelper sqlHelper;


    public Page search(Page page, String keywords) {
        ConditionAndWrapper conditionAndWrapper = new ConditionAndWrapper();
        if (StrUtil.isNotEmpty(keywords)) {
            conditionAndWrapper.and(new ConditionOrWrapper().like("descr", keywords).like("serverName", keywords.trim()).like("listen", keywords.trim()));
        }

        Sort sort = new Sort().add("seq", Direction.DESC);

        page = sqlHelper.findPage(conditionAndWrapper, sort, page, Server.class);

        return page;
    }

    @Transactional
    public void deleteById(String id) {
        sqlHelper.deleteById(id, Server.class);
        sqlHelper.deleteByQuery(new ConditionAndWrapper().eq("serverId", id), Location.class);
    }

    @Transactional
    public void deleteAll() {
        sqlHelper.deleteByQuery(new ConditionAndWrapper().isNotNull("serverName"), Server.class);
        sqlHelper.deleteByQuery(new ConditionAndWrapper().isNotNull("serverId"), Location.class);

    }

    public List<Location> getLocationByServerId(String serverId) {
        return sqlHelper.findListByQuery(new ConditionAndWrapper().eq("serverId", serverId), Location.class);
    }

    @Transactional
    public JSONObject addOver(Server server, String serverParamJson, List<Location> locations) throws Exception {

        if (server.getDef() != null && server.getDef() == 1) {
            clearDef();
        }

        String dbServerID = sqlHelper.insertOrUpdate(server);

        List<Param> paramList = new ArrayList<Param>();
        if (StrUtil.isNotEmpty(serverParamJson) && JSONUtil.isJson(serverParamJson)) {
            paramList = JSONUtil.toList(JSONUtil.parseArray(serverParamJson), Param.class);
        }
        List<String> locationIds = sqlHelper.findIdsByQuery(new ConditionAndWrapper().eq("serverId", server.getId()), Location.class);
        sqlHelper.deleteByQuery(new ConditionOrWrapper().eq("serverId", server.getId()).in("locationId", locationIds), Param.class);

        // 反向插入,保证列表与输入框对应
        Collections.reverse(paramList);
        for (Param param : paramList) {
            param.setServerId(server.getId());
            String dbParamId = sqlHelper.insert(param);
        }

        sqlHelper.deleteByQuery(new ConditionAndWrapper().eq("serverId", server.getId()), Location.class);

        if (locations != null) {
            // 反向插入,保证列表与输入框对应
            Collections.reverse(locations);

            for (Location location : locations) {
                location.setServerId(server.getId());

                String dbLocationId = sqlHelper.insert(location);

                paramList = new ArrayList<Param>();
                if (StrUtil.isNotEmpty(location.getLocationParamJson()) && JSONUtil.isJson(location.getLocationParamJson())) {
                    paramList = JSONUtil.toList(JSONUtil.parseArray(location.getLocationParamJson()), Param.class);
                }

                // 反向插入,保证列表与输入框对应
                Collections.reverse(paramList);
                for (Param param : paramList) {
                    param.setLocationId(location.getId());
                    sqlHelper.insert(param);
                }
            }
        }
        JSONObject tmpJson = null;
        return tmpJson;
    }

    private void clearDef() {
        List<Server> servers = sqlHelper.findListByQuery(new ConditionAndWrapper().eq("def", 1), Server.class);
        for (Server server : servers) {
            server.setDef(0);
            sqlHelper.updateById(server);
        }
    }

    @Transactional
    public void addOverTcp(Server server, String serverParamJson) {
        sqlHelper.insertOrUpdate(server);

        List<String> locationIds = sqlHelper.findIdsByQuery(new ConditionAndWrapper().eq("serverId", server.getId()), Location.class);
        sqlHelper.deleteByQuery(new ConditionOrWrapper().eq("serverId", server.getId()).in("locationId", locationIds), Param.class);
        List<Param> paramList = new ArrayList<Param>();
        if (StrUtil.isNotEmpty(serverParamJson) && JSONUtil.isJson(serverParamJson)) {
            paramList = JSONUtil.toList(JSONUtil.parseArray(serverParamJson), Param.class);
        }

        for (Param param : paramList) {
            param.setServerId(server.getId());
            sqlHelper.insert(param);
        }

        sqlHelper.deleteByQuery(new ConditionAndWrapper().eq("serverId", server.getId()), Location.class);
    }

    public List<Server> getListByProxyType(Integer[] proxyType) {
        Sort sort = new Sort().add("seq", Direction.DESC);
        return sqlHelper.findListByQuery(new ConditionAndWrapper().in("proxyType", proxyType), sort, Server.class);
    }


    public void importServer(String nginxPath) throws Exception {
        List<String> unitNginxPath = initNginx(nginxPath);
        for (String initNginxPath : unitNginxPath) {
            NgxConfig conf = null;
            try {
                conf = NgxConfig.read(initNginxPath);
            } catch (IOException e) {
                e.printStackTrace();
                throw new Exception("文件读取失败");
            }

            List<NgxEntry> servers = conf.findAll(NgxConfig.BLOCK, "server");
            servers.addAll(conf.findAll(NgxConfig.BLOCK, "http", "server"));

            // 翻转一下,便于插入顺序和生成时一样
            Collections.reverse(servers);

            for (NgxEntry ngxEntry : servers) {
                NgxBlock serverNgx = (NgxBlock) ngxEntry;
                NgxParam serverName = serverNgx.findParam("server_name");
                Server server = new Server();
                if (serverName == null) {
                    server.setServerName("");
                } else {
                    server.setServerName(serverName.getValue());
                }

                server.setProxyType(0);

                // 设置server
                List<NgxEntry> listens = serverNgx.findAll(NgxConfig.PARAM, "listen");
                for (NgxEntry item : listens) {
                    NgxParam param = (NgxParam) item;

                    if (server.getListen() == null) {
                        server.setListen((String) param.getValues().toArray()[0]);
                    }

                    if (param.getTokens().stream().anyMatch(item2 -> "ssl".equals(item2.getToken()))) {
                        server.setSsl(1);
                        NgxParam key = serverNgx.findParam("ssl_certificate_key");
                        NgxParam perm = serverNgx.findParam("ssl_certificate");
                        server.setKey(key == null ? "" : key.getValue());
                        server.setPem(perm == null ? "" : perm.getValue());
                    }

                    if (param.getTokens().stream().anyMatch(item2 -> "http2".equals(item2.getToken()))) {
                        server.setHttp2(1);
                    }
                }

                long rewriteCount = serverNgx.getEntries().stream().filter(item -> {
                    if (item instanceof NgxBlock) {
                        NgxBlock itemNgx = (NgxBlock) item;
                        if (itemNgx.getEntries().toString().contains("rewrite")) {
                            return true;
                        }
                        return false;
                    }
                    return false;
                }).count();

                if (rewriteCount > 0) {
                    server.setRewrite(1);
                } else {
                    server.setRewrite(0);
                }

                // 设置location
                List<Location> locations = new ArrayList<>();
                List<NgxEntry> locationBlocks = serverNgx.findAll(NgxBlock.class, "location");
                for (NgxEntry item : locationBlocks) {
                    Location location = new Location();
                    // 目前只支持http段的 proxy_pass
                    NgxParam proxyPassParam = ((NgxBlock) item).findParam("proxy_pass");

                    location.setPath(((NgxBlock) item).getValue());
                    // 如果没有proxy_pass type 0,说明可能是静态文件夹映射 type 1
                    if (proxyPassParam != null) {
                        location.setValue(proxyPassParam.getValue());
                        location.setType(0);
                        //只会返回第一次找到的proxy_set_header ------------------
//                        List<NgxEntry> allEntries = ((NgxBlock) item).findAll(NgxConfig.PARAM, "proxy_set_header");
//                        for (NgxEntry nginxEntry : allEntries) {
//                            NgxParam hostParam = (NgxParam) nginxEntry;
//                            if (hostParam.getValue().contains("Host")) {
//                                location.setProxyHost(hostParam.getValue());
//                            }
//                        }
                    } else {

                        NgxParam rootParam = ((NgxBlock) item).findParam("root");
                        if (rootParam == null) {
                            rootParam = ((NgxBlock) item).findParam("alias");
                        }
                        if (rootParam == null) {
                            continue;
                        }
                        NgxParam tryParam = ((NgxBlock) item).findParam("try_files");
                        if (tryParam != null) {
                            if (rootParam.getName().equals("root")) {
                                location.setRootType("tryroot");
                                location.setTryFiles(tryParam.getValue());
                            } else {
                                location.setRootType("tryalias");
                                location.setTryFiles(tryParam.getValue());
                            }
                        } else {
                            location.setRootType(rootParam.getName());
                        }
                        location.setRootPath(rootParam.getValue());
                        NgxParam indexParam = ((NgxBlock) item).findParam("index");
                        if (indexParam != null) {
                            location.setRootPage(indexParam.getValue());
                        }

                        location.setType(1);
                    }

                    //这边导入的时候其实是要有locatinParam的,这块还可以找try_Files
                    List<NgxEntry> allAddHeaderEntries = ((NgxBlock) item).findAll(NgxConfig.PARAM, "add_header");
                    List<NgxEntry> allProxySetHeaderEntries = ((NgxBlock) item).findAll(NgxConfig.PARAM, "proxy_set_header");
                    List<String> paramList = new ArrayList<String>();

                    for (NgxEntry nginxEntry : allAddHeaderEntries) {
                        JSONObject singlieLoctionParamJson = new JSONObject();
                        NgxParam addHeaderParam = (NgxParam) nginxEntry;
                        singlieLoctionParamJson.put("name", "add_header");
                        singlieLoctionParamJson.put("value", addHeaderParam.getValue());
                        paramList.add(singlieLoctionParamJson.toString());
                    }
                    for (NgxEntry nginxEntry : allProxySetHeaderEntries) {
                        JSONObject singlieLoctionParamJson = new JSONObject();
                        NgxParam proxySetHeaderParam = (NgxParam) nginxEntry;
//                        String reg = "Host.*";
                        //如果不是proxy_set_header Host .... 就设置为其他参数
                        //这块没生效
                        if (proxySetHeaderParam.getValue().contains("Host ")) {
                            location.setProxyHost(proxySetHeaderParam.getValue());
                        } else {
                            singlieLoctionParamJson.put("name", "proxy_set_header");
                            singlieLoctionParamJson.put("value", proxySetHeaderParam.getValue());
//                            System.out.println("导入的proxy_set_header是:" +  proxySetHeaderParam.getValue() );
                            log.info("导入的proxy_set_header是:" + proxySetHeaderParam.getValue());
                            paramList.add(singlieLoctionParamJson.toString());
                        }
                    }

                    location.setLocationParamJson(paramList.toString());
                    locations.add(location);
                }
                server.setDef(0);
                server.setSeq(SnowFlakeUtils.getId());
                //这一块先插入server , location, 然后再更新param
                // insert   => update
                addOver(server, "", locations);
            }

            // 删除临时文件
            FileUtil.del(initNginxPath);
        }

    }

    /**
     * 重新创建配置文件，删除影响解析的行（比如#号开头，但是此行没有其他内容）
     *
     * @param nginxPath
     * @return java.lang.String
     * @author by yanglei 2020/7/5 21:17
     */
    private List<String> initNginx(String nginxPath) {
        // 删除一行内容（java本身没有删除的方法，本方法通过先读取文件的内容（需删除的行数除外），放到list中，在重新写入）
        boolean isDir = FileUtil.isDirectory(nginxPath);
        List<String> unitNginxPaths = new ArrayList<>();

        //如果上传的是一个dir
        if (isDir) {
            //列出所有的conf文件
            List<String> localPathList = FileUtil.listFileNames(nginxPath);
            if (localPathList.size() > 0) {
                for (String fileName : localPathList) {
                    //单个文件
                    if (fileName.endsWith(".conf")) {
                        String parentAbPath = FileUtil.getAbsolutePath(nginxPath);
                        String singleFileAbPath = parentAbPath + "/" + fileName;
                        //需要是绝对路径，不然会去静态目录找
                        List<String> lines = FileUtil.readLines(singleFileAbPath, CharsetUtil.CHARSET_UTF_8);
                        List<String> rs = new ArrayList<>();
                        for (String str : lines) {
                            if (str.trim().indexOf("#") == 0) {
                                continue;
                            }
                            rs.add(str);
                        }
                        String initNginxPath = FileUtil.getTmpDirPath() + UUID.randomUUID().toString();
                        FileUtil.writeLines(rs, initNginxPath, CharsetUtil.CHARSET_UTF_8);
                        unitNginxPaths.add(initNginxPath);
                    }
                }
            }
        } else {
            //单个文件
            List<String> lines = FileUtil.readLines(nginxPath, CharsetUtil.CHARSET_UTF_8);
            List<String> rs = new ArrayList<>();
            for (String str : lines) {
                if (str.trim().indexOf("#") == 0) {
                    continue;
                }
                rs.add(str);
            }
            String initNginxPath = FileUtil.getTmpDirPath() + UUID.randomUUID().toString();
            FileUtil.writeLines(rs, initNginxPath, CharsetUtil.CHARSET_UTF_8);
            unitNginxPaths.add(initNginxPath);

        }
        return unitNginxPaths;
    }

    public void setSeq(String serverId, Integer seqAdd) {
        Server server = sqlHelper.findById(serverId, Server.class);

        List<Server> serverList = sqlHelper.findAll(new Sort("seq", Direction.DESC), Server.class);
        if (serverList.size() > 0) {
            Server tagert = null;
            if (seqAdd < 0) {
                // 下移
                for (int i = 0; i < serverList.size(); i++) {
                    if (serverList.get(i).getSeq() < server.getSeq()) {
                        tagert = serverList.get(i);
                        break;
                    }
                }
            } else {
                // 上移
                System.out.println(server.getSeq());
                for (int i = serverList.size() - 1; i >= 0; i--) {
                    System.out.println(serverList.get(i).getSeq());
                    if (serverList.get(i).getSeq() > server.getSeq()) {
                        tagert = serverList.get(i);
                        break;
                    }
                }
            }

            if (tagert != null) {

                System.err.println("tagert:" + tagert.getServerName() + tagert.getListen());
                System.err.println("server:" + server.getServerName() + server.getListen());

                // 交换seq
                Long seq = tagert.getSeq();
                tagert.setSeq(server.getSeq());
                server.setSeq(seq);

                sqlHelper.updateById(tagert);
                sqlHelper.updateById(server);
            }

        }

    }

}
