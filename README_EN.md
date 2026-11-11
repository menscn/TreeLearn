# TreeLearn

**Give every question a place to come back to.**

TreeLearn is an AI conversation app designed for in-depth learning. It follows a natural learning process: a question comes up while you read, you explore it, and then return to where you left off.

Start a separate line of inquiry from an answer, and your questions and answers grow into a learning tree. Each branch keeps its connection to where it began, so you can retrace your thinking.

[中文](README.md)

---

## Why TreeLearn?

Learning an unfamiliar subject rarely follows a straight line.

Imagine you are learning about database transactions. While reading about isolation levels, you want to understand dirty reads. Once you have explored that question, you might want to know why phantom reads occur.

In a linear conversation, those questions and answers appear in chronological order. By the time you finish exploring, the original answer may be far above. When you return later, finding where each question came from can take more searching.

TreeLearn grew out of my own experience with this problem. Its aim is to let you **follow a question in depth and find your way back to your original train of thought**.

## How a learning session unfolds

**Start with a question**<br>
Enter something you want to understand on the home screen to begin a learning topic.

**Follow a question from an answer**<br>
When something deserves a closer look, create a branch from a completed answer. Each branch has its own context and can branch further.

**Return to where you started**<br>
After exploring a question, return to the source answer and reading position to continue your original line of thought.

**Find your way through the topic again**<br>
Use branch navigation and the tree overview to revisit the paths you have explored, then continue learning when you reopen the topic.

```text
What are database transaction isolation levels?
├── What is a dirty read?
│   └── How can dirty reads be prevented?
└── Why do phantom reads occur?
```

## A look at TreeLearn

<table>
  <tr>
    <td align="center"><img src="assets/readme/screen-home.png" width="220" alt="TreeLearn home screen"><br><b>Home</b></td>
    <td align="center"><img src="assets/readme/hero-chat.png" width="220" alt="Conversation screen with follow-up questions below an answer"><br><b>Conversation</b></td>
  </tr>
  <tr>
    <td align="center"><img src="assets/readme/screen-branch-nav.png" width="220" alt="Branch navigation screen"><br><b>Branches</b></td>
    <td align="center"><img src="assets/readme/screen-library.png" width="220" alt="Learning library screen"><br><b>Library</b></td>
  </tr>
</table>

## Designed around the learning process

### Questions have a starting point

Create a branch from a completed answer to give a new exploration a clear starting point and a way back to its source.

### Exploration has room of its own

Each branch keeps its own context, so later additions to the parent do not change its background. You can explore one direction while preserving your original train of thought.

### Your learning path can be revisited

Branch navigation and the tree overview show the directions you have explored, helping you find the question you want to return to.

### Records stay on your device

On Android, learning records are stored locally and no account is required. Using an online AI service requires a provider and API key; relevant request content is sent to the provider, and charges may apply. Enabling web search also sends requests to the selected search service.

The Windows desktop app supports local learning history, drafts, model settings, and streaming conversations. Configure your API key in the desktop app before use. See the [desktop guide](desktop/README.md) for current capabilities and storage; desktop and Android data do not sync automatically. The [desktop and web specification](Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md) describes earlier development work.

## License

This project is not licensed as open source. Use, copying, modification, and distribution of the code are subject to the terms in [LICENSE](LICENSE). Third-party components remain subject to their respective licenses.

Copyright © 2026 TreeLearn Project. All rights reserved.
