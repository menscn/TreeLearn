import { describe, expect, it } from "vitest";
import { bundledEntries } from "../../src/platform/bundledFixtures";
import { payloadOf, type FixturePayload } from "../../src/platform/fixtureNodes";
import { parseEnvelope } from "../../src/platform/RuntimePort";
import {
  answerBlocks,
  branchesFrom,
  chatFrom,
  homeFrom,
  recommendationsFrom,
  settingsFrom,
  treeFrom,
  topicsFrom,
  workspaceDataFrom,
  type PayloadMap
} from "../../src/store/workspaceData";

function payload(name: string): FixturePayload | null {
  const text = bundledEntries()[name];
  if (text === undefined) return null;
  const result = parseEnvelope(text, name);
  return result.ok ? payloadOf(result.envelope) : null;
}

function allPayloads(): PayloadMap {
  return {
    home: payload("home"),
    library: payload("library"),
    chat: payload("chat"),
    navigation: payload("navigation"),
    treecanvas: payload("treecanvas"),
    settings: payload("settings"),
    settingskey: payload("settingskey"),
    provideradmin: payload("provideradmin"),
    modelsettings: payload("modelsettings"),
    searchservicesettings: payload("searchservicesettings"),
    voicesettings: payload("voicesettings"),
    filemanager: payload("filemanager")
  };
}

describe("夹具读成工作区视图模型", () => {
  it("学习库两卡读成两个主题，meta 里的分支数不是猜的", () => {
    const topics = topicsFrom(allPayloads());
    expect(topics.map((t) => t.title)).toEqual([
      "注意力权重可视化与模型可解释性的关系",
      "系统理解 Transformer 的注意力机制"
    ]);
    expect(topics[0]?.meta).toBe("1 个分支 · 3天前");
    expect(topics[1]?.meta).toBe("2 个分支 · 3天前");
    expect(topics[0]?.branchCount).toBe(1);
    expect(topics[1]?.branchCount).toBe(2);
    expect(topics[0]?.summary).toContain("注意力头消融");
    expect(topics[0]?.questions[0]).toContain("关键头");
    expect(topics[1]?.questions).toEqual([]);
    expect(topics.map((topic) => topic.status)).toEqual(["in_progress", "in_progress"]);
  });

  it("状态先读每条 meta；缺状态时只按分段余额回填，不凭默认值归类", () => {
    const payloads = allPayloads();
    const library = payloads.library;
    expect(library).not.toBeNull();

    const withoutSegments = {
      ...library!,
      nodes: library!.nodes.filter((node) => !node.id.startsWith("library_segment_"))
    };
    expect(topicsFrom({ ...payloads, library: withoutSegments }).map((topic) => topic.status))
      .toEqual(["unknown", "unknown"]);

    const explicit = structuredClone(library!);
    const metas = explicit.nodes.filter((node) => node.id === "library_topic_meta");
    metas[0]!.text = "1 个分支 · 已完成";
    metas[1]!.text = "2 个分支 · 进行中";
    expect(topicsFrom({ ...payloads, library: explicit }).map((topic) => topic.status))
      .toEqual(["done", "in_progress"]);
  });

  it("主页模型读取真实草稿占位、模型与标题，缺失时返回 null", () => {
    expect(homeFrom(allPayloads())).toEqual({
      draftPlaceholder: "输入你的根问题，例如：帮我系统理解 Transformer 的注意力机制",
      model: "V4.1 Flash",
      recommendationsTitle: "猜你想学",
      recentTitle: "历史主题",
      recentCount: "2 个"
    });
    expect(homeFrom({})).toBeNull();
    expect(workspaceDataFrom(allPayloads()).home).not.toBeNull();
  });

  it("推荐两行来自主页夹具，并落到有对话内容的主题 0", () => {
    const recs = recommendationsFrom(allPayloads());
    expect(recs).toHaveLength(2);
    expect(recs[0]?.tag).toBe("顺着你学的");
    expect(recs[0]?.text).toBe("注意力头消融实验中如何区分关键头与补偿性替代头");
    expect(recs.every((r) => r.topicIndex === 0)).toBe(true);
  });

  it("主题 0 的回答逐字进来，结构识别出标题、步骤与列表", () => {
    const chat = chatFrom(allPayloads());
    expect(chat).not.toBeNull();
    expect(chat?.topicIndex).toBe(0);
    expect(chat?.title).toContain("注意力权重可视化");
    expect(chat?.model).toBe("V4.1 Flash");
    expect(chat?.time).toBe("3天前");
    expect(chat?.suggestions).toHaveLength(3);
    expect(chat?.answer.length).toBeGreaterThan(1500);
    expect(chat?.answer.startsWith("核心先说结论")).toBe(true);
    expect(chat?.answer.endsWith("接管了。")).toBe(true);
    const kinds = chat?.blocks.map((b) => b.kind) ?? [];
    expect(kinds.filter((k) => k === "h2")).toHaveLength(3);
    expect(kinds.filter((k) => k === "h3").length).toBeGreaterThanOrEqual(10);
    expect(kinds.filter((k) => k === "list").length).toBeGreaterThanOrEqual(4);
    const listItems = (chat?.blocks ?? []).flatMap((b) => b.items ?? []);
    expect(listItems.some((item) => item.startsWith("置零（zero ablation）"))).toBe(true);
    expect(listItems.some((item) => item.startsWith("•"))).toBe(false);
  });

  it("回答块解析不丢换行正文", () => {
    const blocks = answerBlocks("第一段\n\n一、小节\n\n1. 步骤\n说明文字\n\n• 条目一\n\n• 条目二");
    expect(blocks.map((b) => b.kind)).toEqual(["p", "h2", "h3", "p", "list"]);
    expect(blocks[4]?.items).toEqual(["条目一", "条目二"]);
  });

  it("分支模型只认导航夹具里的根与已知数量", () => {
    const branches = branchesFrom(allPayloads());
    expect(branches).not.toBeNull();
    expect(branches?.topicIndex).toBe(0);
    expect(branches?.title).toBe("注意力权重可视化与模型可解释性的关系");
    expect(branches?.rootChip).toBe("根分支 · 不可归档");
    expect(branches?.rootMeta).toBe("当前 · 0 子分支");
    expect(branches?.knownChildren).toBe(0);
    expect(branches?.captured).toBe(true);
    expect(branches?.summary).toContain("本分支从注意力权重可视化");
  });

  it("知识树用真实标题与主题副标题，不生成示意子节点", () => {
    const tree = treeFrom(allPayloads());
    expect(tree?.title).toBe("总览画布");
    expect(tree?.subtitle).toBe("注意力权重可视化与模型可解释性的关系");
    expect(tree?.topicIndex).toBe(0);
    expect(tree?.rootLabel).toBe("注意力权重可视化与模型可解释性的关系");
  });

  it("六类设置都从真实夹具读出标题与字段", () => {
    const categories = settingsFrom(allPayloads());
    expect(categories.map((c) => c.id)).toEqual([
      "api-key",
      "provider",
      "model",
      "search",
      "voice",
      "files"
    ]);
    expect(categories.map((c) => c.title)).toEqual([
      "API KEY · DeepSeek",
      "提供商管理",
      "模型设置",
      "搜索服务",
      "语音",
      "文件管理"
    ]);
    const fields = (id: string) => categories.find((c) => c.id === id)?.groups.flatMap((g) => g.fields) ?? [];
    expect(categories.every((c) => c.groups.length > 0)).toBe(true);
    expect(categories.every((c) => c.available)).toBe(true);
    expect(fields("api-key")).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ label: "sk-••••••••••f9b8", value: "可用" })
      ])
    );
    expect(fields("provider")).toEqual(
      expect.arrayContaining([expect.objectContaining({ label: "DeepSeek" })])
    );
    expect(fields("model").filter((f) => f.value === "deepseek-flash")).toHaveLength(3);
    expect(fields("search")).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ label: "智谱" }),
        expect.objectContaining({ label: "Tavily" }),
        expect.objectContaining({ label: "博查" }),
        expect.objectContaining({ label: "每次检索 10 条" })
      ])
    );
    expect(fields("search").find((f) => f.control === "switch")?.state).toBe("off");
    expect(fields("voice")).toEqual(
      expect.arrayContaining([expect.objectContaining({ label: "系统 TTS" })])
    );
    expect(categories.find((c) => c.id === "voice")?.actions).toEqual(
      expect.arrayContaining([expect.objectContaining({ label: "试听" })])
    );
    expect(fields("files")).toEqual(
      expect.arrayContaining([expect.objectContaining({ label: "已占用 0 B" })])
    );
    const withoutProvider = settingsFrom({ ...allPayloads(), provideradmin: null });
    expect(withoutProvider.find((c) => c.id === "provider")?.available).toBe(false);
    expect(withoutProvider).toHaveLength(6);
  });

  it("主题 1 的分支数来自 meta，但明确标记内容未采集", () => {
    const data = workspaceDataFrom(allPayloads());
    expect(data.branches).toHaveLength(2);
    const second = data.branches[1];
    expect(second?.topicIndex).toBe(1);
    expect(second?.captured).toBe(false);
    expect(second?.knownChildren).toBe(1);
    expect(second?.summary).toContain("Transformer 的注意力机制");
    expect(data.chat?.topicIndex).toBe(0);
  });

  it("夹具缺失时返回空模型而不是编内容", () => {
    expect(topicsFrom({})).toEqual([]);
    expect(recommendationsFrom({})).toEqual([]);
    expect(chatFrom({})).toBeNull();
    expect(branchesFrom({})).toBeNull();
    expect(treeFrom({})).toBeNull();
    expect(settingsFrom({})).toEqual([]);
    const empty = workspaceDataFrom({}, "error");
    expect(empty.status).toBe("error");
    expect(empty.topics).toEqual([]);
    expect(empty.chat).toBeNull();
  });
});
