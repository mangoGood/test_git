const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;

// 中间件
app.use(cors());
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));

// 静态文件服务 - 前端页面
app.use(express.static(path.join(__dirname, '..')));

// API路由
const workflowRoutes = require('./routes/workflows');
app.use('/api/workflows', workflowRoutes);

// 健康检查
app.get('/api/health', (req, res) => {
    res.json({
        success: true,
        message: '服务运行正常',
        timestamp: new Date().toISOString()
    });
});

// 错误处理中间件
app.use((err, req, res, next) => {
    console.error('服务器错误:', err);
    res.status(500).json({
        success: false,
        message: '服务器内部错误',
        error: err.message
    });
});

// 启动服务器
app.listen(PORT, () => {
    console.log(`服务器已启动，监听端口: ${PORT}`);
    console.log(`API地址: http://localhost:${PORT}/api`);
    console.log(`前端页面: http://localhost:${PORT}/admin-dashboard.html`);
});

module.exports = app;
