const express = require('express');
const router = express.Router();
const Workflow = require('../models/workflow');
const workflowExecutor = require('../services/workflowService');
const kafkaService = require('../services/kafkaService');

// 创建新的同步任务
router.post('/', async (req, res) => {
    try {
        const { name, sourceConnection, targetConnection, migrationMode } = req.body;
        
        if (!name) {
            return res.status(400).json({
                success: false,
                message: '任务名称不能为空'
            });
        }
        
        // 创建工作流记录
        const workflow = await Workflow.create({
            name,
            sourceConnection: sourceConnection || 'default-source',
            targetConnection: targetConnection || 'default-target',
            migrationMode: migrationMode || 'full'
        });
        
        // 发送消息到 Kafka
        try {
            await kafkaService.sendTaskCreatedMessage(
                workflow.id,
                sourceConnection || 'default-source',
                targetConnection || 'default-target',
                migrationMode || 'full'
            );
        } catch (kafkaError) {
            console.error('Failed to send Kafka message:', kafkaError);
        }
        
        // 启动工作流执行
        workflowExecutor.startWorkflow(workflow.id);
        
        res.status(201).json({
            success: true,
            message: '同步任务创建成功',
            data: workflow
        });
    } catch (error) {
        console.error('创建任务失败:', error);
        res.status(500).json({
            success: false,
            message: '创建任务失败',
            error: error.message
        });
    }
});

// 获取所有任务列表
router.get('/', async (req, res) => {
    try {
        const page = parseInt(req.query.page) || 1;
        const pageSize = parseInt(req.query.pageSize) || 10;
        
        // 从请求中获取用户ID（这里简化处理，实际应该从token中解析）
        const userId = req.query.userId ? parseInt(req.query.userId) : null;
        
        const result = await Workflow.findAll(page, pageSize, userId);
        
        res.json({
            success: true,
            data: result
        });
    } catch (error) {
        console.error('获取任务列表失败:', error);
        res.status(500).json({
            success: false,
            message: '获取任务列表失败',
            error: error.message
        });
    }
});

// 获取单个任务详情
router.get('/:id', async (req, res) => {
    try {
        const { id } = req.params;
        const workflow = await Workflow.findById(id);
        
        if (!workflow) {
            return res.status(404).json({
                success: false,
                message: '任务不存在'
            });
        }
        
        // 获取任务日志
        const logs = await Workflow.getLogs(id);
        
        res.json({
            success: true,
            data: {
                ...workflow,
                logs
            }
        });
    } catch (error) {
        console.error('获取任务详情失败:', error);
        res.status(500).json({
            success: false,
            message: '获取任务详情失败',
            error: error.message
        });
    }
});

// 暂停任务
router.post('/:id/pause', async (req, res) => {
    try {
        const { id } = req.params;
        
        await workflowExecutor.stopWorkflow(id);
        
        res.json({
            success: true,
            message: '任务已暂停'
        });
    } catch (error) {
        console.error('暂停任务失败:', error);
        res.status(500).json({
            success: false,
            message: '暂停任务失败',
            error: error.message
        });
    }
});

// 删除任务
router.delete('/:id', async (req, res) => {
    try {
        const { id } = req.params;
        
        // 先停止运行中的任务
        await workflowExecutor.stopWorkflow(id);
        
        // 删除任务
        const deleted = await Workflow.delete(id);
        
        if (!deleted) {
            return res.status(404).json({
                success: false,
                message: '任务不存在'
            });
        }
        
        res.json({
            success: true,
            message: '任务删除成功'
        });
    } catch (error) {
        console.error('删除任务失败:', error);
        res.status(500).json({
            success: false,
            message: '删除任务失败',
            error: error.message
        });
    }
});

module.exports = router;
