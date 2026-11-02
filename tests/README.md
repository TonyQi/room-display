# 协议联调测试

只对独立测试服务器执行，会注册30台测试设备并保留测试数据。设置 ROOM_ADMIN_PASSWORD、ROOM_ENROLLMENT_KEY 和可选 ROOM_TEST_URL，然后执行 python protocol-integration.py。不要直接对生产数据库执行。该测试是协议客户端测试，不能替代Android模拟器或实机验收。

服务器单元/集成测试：server目录运行 mvn test，使用独立内存数据库。网页模型测试：web目录 npm test。网页真实联调测试要求Chrome与Playwright，并在项目工作目录work/test-credentials.json准备username/password/enrollmentKey/url，不要提交或分享该凭据文件。
