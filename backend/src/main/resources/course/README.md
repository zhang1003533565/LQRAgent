# Python 课程数据目录

这个目录用于存放比赛项目的课程基础数据。

建议结构：

- `outline.json`
  - 课程章节大纲
- `knowledge_graph.json`
  - 知识点和前置依赖
- `seed_kb/`
  - 公共知识库种子文件
- `examples/`
  - 示例代码

原则：

- 图谱负责导航
- 种子知识库负责 RAG
- 两者必须使用统一的知识点 ID
