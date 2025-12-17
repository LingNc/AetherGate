## 版本号命名规范
1. 主版本号.次版本号.修订号
## 分支策略
1. 拥有两个长期分支：
   - main: 绝对稳定。
   - develop (开发分支): 最新的开发进度。所有的新功能都先合并到这里。
2. 开发新功能
```bash
git checkout -b feat/config-refactor develop
# ... 写代码，提交 ...
```
3. 合并功能
```bash
git checkout develop
git merge feat/config-refactor
# 删掉临时的特性分支
git branch -d feat/config-refactor
```
4. 发布版本：当develop上累积了足够的更新，合并到main，打标签
```bash
git checkout main
git merge develop
git tag -a v1.5.0 -m "Release v1.5.0"
git push origin main --tags
```
5. 修复：如果发布后发现有严重 bug
从 main 切出 fix/v1.5.1 分支进行修复
测试稳定后合并回develop和main