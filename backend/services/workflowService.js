const Workflow = require('../models/workflow');

// 模拟工作流执行器
class WorkflowExecutor {
    constructor() {
        this.runningWorkflows = new Map();
    }
    
    // 启动工作流
    async startWorkflow(workflowId) {
        // 检查是否已经在运行
        if (this.runningWorkflows.has(workflowId)) {
            return;
        }
        
        // 更新状态为运行中
        await Workflow.updateStatus(workflowId, 'running', 0);
        await Workflow.addLog(workflowId, 'info', '工作流开始执行');
        
        // 创建工作流执行上下文
        const workflowContext = {
            id: workflowId,
            progress: 0,
            timer: null
        };
        
        this.runningWorkflows.set(workflowId, workflowContext);
        
        // 模拟工作流执行过程
        this.simulateExecution(workflowId);
        
        return workflowContext;
    }
    
    // 模拟执行过程
    async simulateExecution(workflowId) {
        const stages = [
            { progress: 10, message: '正在连接源数据库...', delay: 2000 },
            { progress: 20, message: '正在连接目标数据库...', delay: 2000 },
            { progress: 30, message: '正在分析表结构...', delay: 3000 },
            { progress: 40, message: '正在创建同步任务...', delay: 2000 },
            { progress: 50, message: '正在进行全量同步...', delay: 5000 },
            { progress: 70, message: '正在进行增量同步...', delay: 4000 },
            { progress: 90, message: '正在验证数据一致性...', delay: 3000 },
            { progress: 100, message: '同步任务完成', delay: 1000 }
        ];
        
        for (const stage of stages) {
            await this.delay(stage.delay);
            
            // 检查工作流是否被取消
            if (!this.runningWorkflows.has(workflowId)) {
                return;
            }
            
            await Workflow.updateStatus(workflowId, 'running', stage.progress);
            await Workflow.addLog(workflowId, 'info', stage.message);
            
            // 更新上下文
            const context = this.runningWorkflows.get(workflowId);
            if (context) {
                context.progress = stage.progress;
            }
        }
        
        // 完成工作流
        await Workflow.updateStatus(workflowId, 'completed', 100);
        await Workflow.addLog(workflowId, 'info', '工作流执行成功完成');
        
        // 清理
        this.runningWorkflows.delete(workflowId);
    }
    
    // 停止工作流
    async stopWorkflow(workflowId) {
        const context = this.runningWorkflows.get(workflowId);
        if (context) {
            this.runningWorkflows.delete(workflowId);
            await Workflow.updateStatus(workflowId, 'paused', context.progress);
            await Workflow.addLog(workflowId, 'warning', '工作流被手动暂停');
        }
    }
    
    // 延迟函数
    delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
    
    // 获取运行中的工作流
    getRunningWorkflows() {
        return Array.from(this.runningWorkflows.keys());
    }
}

// 单例模式
const executor = new WorkflowExecutor();

module.exports = executor;
