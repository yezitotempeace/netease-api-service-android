// 网易云 API 服务 —— 由内置 Node 运行时启动
process.chdir(__dirname);

var serveNcmApi = require('@neteasecloudmusicapienhanced/api/server').serveNcmApi;

serveNcmApi({ port: 3000, host: '127.0.0.1', checkVersion: false })
  .then(function () {
    console.log('[NCM-API] READY http://127.0.0.1:3000');
  })
  .catch(function (e) {
    console.error('[NCM-API] FAILED', (e && e.stack) || e);
    process.exit(1);
  });
