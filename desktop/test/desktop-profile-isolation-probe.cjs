/* Probe startup switches before launching the production entry; no user data is read. */
const { app } = require('electron');
app.setName('知芽');
app.whenReady().then(() => {
  console.log('PROFILE_PROBE ' + app.getPath('userData'));
  app.exit(0);
});
