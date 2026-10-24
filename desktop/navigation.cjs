/** 渲染进程只加载包内的 file:// 页面；外部地址与伪协议一律不导航。 */
function isAllowedNavigation(url) {
  return typeof url === "string" && url.startsWith("file://");
}

module.exports = { isAllowedNavigation };
