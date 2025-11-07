// 多用户协作测试脚本
// 在浏览器控制台中运行此脚本进行测试

// 测试步骤：
// 1. 确保两个浏览器窗口都登录了不同的用户
// 2. 都在同一个文档页面
// 3. 在控制台中运行此脚本

console.log('🔧 多用户协作测试脚本加载完成');

// 测试函数
function testCollaboration() {
    console.log('🚀 开始多用户协作测试...');

    // 测试1: 检查WebSocket连接
    console.log('📡 测试1: 检查WebSocket连接状态');
    if (typeof stompClient !== 'undefined' && stompClient && stompClient.connected) {
        console.log('✅ WebSocket已连接');
    } else {
        console.log('❌ WebSocket未连接');
        return;
    }

    // 测试2: 发送测试操作
    console.log('📝 测试2: 发送测试编辑操作');
    const testOperation = {
        type: 'INSERT',
        data: '[测试内容 - ' + new Date().toLocaleTimeString() + ']',
        position: 0
    };

    // 模拟发送操作（调用页面中的函数）
    if (typeof sendOperation === 'function') {
        sendOperation(testOperation);
        console.log('✅ 测试操作已发送');
    } else {
        console.log('❌ sendOperation函数不存在');
    }

    // 测试3: 检查用户状态
    console.log('👥 测试3: 检查在线用户状态');
    setTimeout(() => {
        if (typeof updateOnlineUsersList === 'function') {
            updateOnlineUsersList();
            console.log('✅ 用户状态已更新');
        } else {
            console.log('❌ updateOnlineUsersList函数不存在');
        }
    }, 2000);

    console.log('🎯 测试完成！请观察页面变化和日志输出');
}

// 监听WebSocket消息
function monitorWebSocket() {
    console.log('👂 开始监听WebSocket消息...');

    // 保存原始订阅函数
    const originalSubscribe = stompClient.subscribe;

    // 包装订阅函数以监听消息
    stompClient.subscribe = function(destination, callback) {
        console.log('📡 订阅频道:', destination);

        const wrappedCallback = function(message) {
            console.log('📨 收到消息:', destination, message.body);
            try {
                const data = JSON.parse(message.body);
                console.log('📋 消息内容:', data);
            } catch (e) {
                console.log('📋 原始消息:', message.body);
            }
            return callback(message);
        };

        return originalSubscribe.call(this, destination, wrappedCallback);
    };

    console.log('✅ WebSocket消息监听已启用');
}

// 暴露全局函数
window.testCollaboration = testCollaboration;
window.monitorWebSocket = monitorWebSocket;

console.log('🎮 可用命令:');
console.log('  testCollaboration() - 运行协作测试');
console.log('  monitorWebSocket() - 监听WebSocket消息');