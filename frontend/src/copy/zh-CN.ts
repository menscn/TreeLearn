/**
 * 简中文案表的唯一正文。
 *
 * 键名沿用 `android/app/src/main/res/values/strings.xml`，取值逐字对齐 Android 现状；
 * Android 没有对应概念的两项（夹具基线缺失、本地服务不可达）是 Web 独有状态，就地另起键。
 * 本期只做简中：不要求任何第二语言齐备（`.codex/rules/frontend.md` 的 user-copy guard）。
 */
export const zhCN = {
  app_name: "知芽",
  tab_home: "主页",
  tab_library: "学习库",
  tree_loading: "正在读取这棵树…",
  tree_empty: "这棵树还没有分支。",
  tree_error: "这棵树暂时读不出来，稍后重试即可。",
  chat_generating_reasoning: "正在思考",
  chat_generating_answer: "正在生成回答",
  chat_stopped: "已停止",
  chat_failed: "生成中断，以上为部分回答，不可作为分支来源。",
  notice_key_missing: "还没有配置 DeepSeek API Key，先在设置里填一个再发送。",
  notice_network_interrupted: "网络中断了，检查网络后重试。",
  state_fixture_unavailable: "这一屏还没有采集到基线截图与状态快照，先跑阶段一的采集。",
  state_service_unreachable: "本地服务没有响应，确认它已经启动后重试。"
} as const;
