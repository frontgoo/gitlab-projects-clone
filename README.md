### 通过gitlab Api自动下载gitLab上的所有项目



### 背景

现在越来越多的公司采用gitlab来管理代码。但是公司越来越大，项目越来越多，一个个clone比较麻烦，于是写个java程序批量clone


### 思路

gitlab有提供api来获取git数据，利用这些信息clone项目

参考文档：[https://docs.gitlab.com/ee/api/projects.html#list-all-projects](https://links.jianshu.com/go?to=https%3A%2F%2Fdocs.gitlab.com%2Fee%2Fapi%2Fprojects.html%23list-all-projects)



### 步骤：

#### 1、申请gitlab token

 进入gitlab Settings页面, 点击Access Tokens标签

![image-20200429165609200](https://tva1.sinaimg.cn/large/007S8ZIlgy1geaqonuvnmj32k20qu7fs.jpg)



#### 2 、实现逻辑

- 1、通过API获取分组列表

- 2、遍历分组列表

- 3、通过指定分组名称获取项目列表

- 4、遍历项目列表

- 5、通过指定项目ID获取最近修改的分支名称

- 6、克隆指定分支的项目到指定目录

  

核心代码见 `GitlabProjectCloneService.java`
gitlab api 版本升级至v4

base clone from github地址：[https://github.com/huchao1009/gitlab-projects-clone](https://github.com/huchao1009/gitlab-projects-clone)