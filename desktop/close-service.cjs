function closeService(event, getService, clearService, showError) {
  const service = getService();
  if (!service) return;
  try { service.close(); clearService(); }
  catch {
    event.preventDefault();
    showError('保存失败', '未能保存正在生成的内容。请检查磁盘空间和权限，确认内容后重试。');
  }
}
module.exports = { closeService };
