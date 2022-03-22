package com.opensource.gitlab.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensource.gitlab.vo.GitBranch;
import com.opensource.gitlab.vo.GitGroup;
import com.opensource.gitlab.vo.GitProject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 通过gitlab Api自动下载gitLab上的所有项目
 */
@Service
@Slf4j
public class GitlabProjectCloneService {

    @Value("${git.gitlabUrl}")
    private String gitlabUrl;

    @Value("${git.privateToken}")
    private String privateToken;

    @Value("${git.projectDir}")
    private String projectDir;

    @Value("${git.cloneType:http}")
    private String cloneType;

    ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    RestTemplate restTemplate;

    private static final String DEFAULT_BRANCH = "master";
    private static final String DEFAULT_CLONE_TYPE = "ssh";

    @PostConstruct
    private void start() {
        File execDir = new File(projectDir);
        log.info("start get gitlab projects");
        List<GitGroup> groups = getGroups();
        try {
            log.info(objectMapper.writeValueAsString(groups));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
        }
        for (GitGroup group : groups) {
            List<GitProject> projects = getProjectsByGroup(group);
            for (GitProject project : projects) {
                List<GitBranch> branches = getBranches(project.getId());
                if (CollectionUtils.isEmpty(branches)) {
                    log.warn("branches is empty, break project...");
                    continue;
                }
                clone(DEFAULT_BRANCH, project, execDir);
                File subExecDir = new File(projectDir + File.separator + project.getPathWithNamespace());
                for (GitBranch branch : branches) {
                    if (DEFAULT_BRANCH.equals(branch.getName())) {
                        continue;
                    }
                    checkoutOtherBranch(branch.getName(), project, subExecDir);
                }

            }
        }
        log.info("end get gitlab projects");
    }

    /**
     * 获取所有项目
     *
     * @return
     */
    private List<GitProject> getAllProjects() {
        String url = gitlabUrl + "/api/v4/projects?per_page={per_page}&private_token={private_token}";
        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("per_page", "100");
        uriVariables.put("private_token", privateToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity entity = new HttpEntity<>(headers);
        ParameterizedTypeReference<List<GitProject>> responseType = new ParameterizedTypeReference<List<GitProject>>() {
        };
        ResponseEntity<List<GitProject>> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, responseType, uriVariables);
        if (HttpStatus.OK == responseEntity.getStatusCode()) {
            return responseEntity.getBody();
        }
        return null;
    }

    /**
     * 获取指定分组下的项目
     *
     * @param group
     * @return
     */
    private List<GitProject> getProjectsByGroup(GitGroup group) {
        String url = gitlabUrl + "/api/v4/groups/{group}/projects?per_page={per_page}&private_token={private_token}";
        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("group", String.valueOf(group.getId()));
        uriVariables.put("per_page", "100");
        uriVariables.put("private_token", privateToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity entity = new HttpEntity<>(headers);
        ParameterizedTypeReference<List<GitProject>> responseType = new ParameterizedTypeReference<List<GitProject>>() {
        };

        ResponseEntity<List<GitProject>> responseEntity;
        try {
            responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, responseType, uriVariables);
        } catch (RestClientException e) {
            log.error("RestClientException: ", e);
            return new ArrayList<>();
        }
        if (HttpStatus.OK == responseEntity.getStatusCode()) {
            return responseEntity.getBody();
        }
        return new ArrayList<>();
    }

    /**
     * 获取分组列表
     *
     * @return
     */
    private List<GitGroup> getGroups() {
        String url = gitlabUrl + "/api/v4/groups?private_token={private_token}";
        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("private_token", privateToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity entity = new HttpEntity<>(headers);
        ParameterizedTypeReference<List<GitGroup>> responseType = new ParameterizedTypeReference<List<GitGroup>>() {
        };
        ResponseEntity<List<GitGroup>> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, responseType, uriVariables);
        if (HttpStatus.OK == responseEntity.getStatusCode()) {
            return responseEntity.getBody();
        }
        return null;
    }

    /**
     * 获取最近修改的分支名称
     *
     * @param projectId 项目ID
     * @return
     */
    private String getLastActivityBranchName(Long projectId) {
        List<GitBranch> branches = getBranches(projectId);
        if (CollectionUtils.isEmpty(branches)) {
            return "";
        }
        GitBranch gitBranch = getLastActivityBranch(branches);
        return gitBranch.getName();
    }

    /**
     * 获取指定项目的分支列表
     * https://docs.gitlab.com/ee/api/branches.html#branches-api
     *
     * @param projectId 项目ID
     * @return
     */
    private List<GitBranch> getBranches(Long projectId) {
        String url = gitlabUrl + "/api/v4/projects/{projectId}/repository/branches?private_token={privateToken}";
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("projectId", projectId);
        uriVariables.put("privateToken", privateToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity entity = new HttpEntity<>(headers);
        ParameterizedTypeReference<List<GitBranch>> responseType = new ParameterizedTypeReference<List<GitBranch>>() {
        };
        ResponseEntity<List<GitBranch>> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, responseType, uriVariables);
        if (HttpStatus.OK == responseEntity.getStatusCode()) {
            return responseEntity.getBody();
        }
        return null;
    }

    /**
     * 获取最近修改的分支
     *
     * @param gitBranches 分支列表
     * @return
     */
    private GitBranch getLastActivityBranch(final List<GitBranch> gitBranches) {
        GitBranch lastActivityBranch = gitBranches.get(0);
        for (GitBranch gitBranch : gitBranches) {
            if (gitBranch.getCommit().getCommittedDate().getTime() > lastActivityBranch.getCommit().getCommittedDate().getTime()) {
                lastActivityBranch = gitBranch;
            }
        }
        return lastActivityBranch;
    }

    private void checkoutOtherBranch(String branchName, GitProject gitProject, File execDir) {
        String command = String.format("git checkout -b %s origin/%s", branchName, branchName);
        log.info("start exec command : " + command);
        try {
            Process exec = Runtime.getRuntime().exec(command, null, execDir);
            exec.waitFor();
            String successResult = StreamUtils.copyToString(exec.getInputStream(), StandardCharsets.UTF_8);
            String errorResult = StreamUtils.copyToString(exec.getErrorStream(), StandardCharsets.UTF_8);
            log.info("successResult: " + successResult);
            log.info("errorResult: " + errorResult);
            log.info("================================");
        } catch (Exception e) {
            log.info("command : " + command);
            log.error(e.getMessage(), e);
        }
    }

    private void clone(String branchName, GitProject gitProject, File execDir) {
        String cloneUrl;
        if(DEFAULT_CLONE_TYPE.toLowerCase().equals(cloneType)){
            cloneUrl = gitProject.getSshUrlToRepo();
        }else {
            cloneUrl = gitProject.getHttpUrlToRepo().replace("http://", String.format("http://oauth2:%s@", privateToken));
        }
        String command = String.format("git clone -b %s %s %s", branchName, cloneUrl, gitProject.getPathWithNamespace());
        log.info("start exec command : " + command);
        try {
            Process exec = Runtime.getRuntime().exec(command, null, execDir);
            exec.waitFor();
            String successResult = StreamUtils.copyToString(exec.getInputStream(), StandardCharsets.UTF_8);
            String errorResult = StreamUtils.copyToString(exec.getErrorStream(), StandardCharsets.UTF_8);
            log.info("successResult: " + successResult);
            log.info("errorResult: " + errorResult);
            log.info("================================");
        } catch (Exception e) {
            log.info("command : " + command);
            log.error(e.getMessage(), e);
        }
    }
}
