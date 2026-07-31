# HYEEE Agent Hub 前端

独立的静态前端目录，不修改原有 HMDP 页面与后端代码。

通过现有 Nginx 启动后访问：`http://localhost:8080/agent-platform/`。

已对接现有接口：帖子、点赞、评论、话题热榜、秒杀下单和 AI SSE 对话。Token 余额、兑换和收藏因当前后端尚无对应数据模型或接口，界面会明确提示“待接入”，不会伪造持久化结果。
