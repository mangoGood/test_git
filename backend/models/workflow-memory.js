const { v4: uuidv4 } = require('uuid');

class WorkflowMemory {
    constructor() {
        this.workflows = new Map();
        this.logs = new Map();
    }
    
    create(data) {
        const id = uuidv4();
        const { name, sourceConnection, targetConnection } = data;
        
        const workflow = {
            id,
            name,
            source_connection: sourceConnection || 'default-source',
            target_connection: targetConnection || 'default-target',
            status: 'pending',
            progress: 0,
            is_billing: 1,
            created_at: new Date().toISOString(),
            updated_at: new Date().toISOString(),
            completed_at: null,
            error_message: null
        };
        
        this.workflows.set(id, workflow);
        this.logs.set(id, []);
        
        return { id, name, status: 'pending' };
    }
    
    findAll(page = 1, pageSize = 10) {
        const list = Array.from(this.workflows.values())
            .sort((a, b) => new Date(b.created_at) - new Date(a.created_at));
        
        const total = list.length;
        const start = (page - 1) * pageSize;
        const end = start + pageSize;
        const paginatedList = list.slice(start, end);
        
        return {
            list: paginatedList,
            total,
            page,
            pageSize
        };
    }
    
    findById(id) {
        return this.workflows.get(id) || null;
    }
    
    updateStatus(id, status, progress = null, errorMessage = null) {
        const workflow = this.workflows.get(id);
        if (!workflow) return false;
        
        workflow.status = status;
        if (progress !== null) {
            workflow.progress = progress;
        }
        if (errorMessage) {
            workflow.error_message = errorMessage;
        }
        workflow.updated_at = new Date().toISOString();
        
        if (status === 'completed' || status === 'failed') {
            workflow.completed_at = new Date().toISOString();
            workflow.is_billing = 0;
        }
        
        return true;
    }
    
    delete(id) {
        this.workflows.delete(id);
        this.logs.delete(id);
        return true;
    }
    
    addLog(workflowId, level, message) {
        if (!this.logs.has(workflowId)) {
            this.logs.set(workflowId, []);
        }
        
        const logs = this.logs.get(workflowId);
        logs.push({
            id: logs.length + 1,
            workflow_id: workflowId,
            level,
            message,
            created_at: new Date().toISOString()
        });
        
        return logs.length;
    }
    
    getLogs(workflowId) {
        return this.logs.get(workflowId) || [];
    }
}

module.exports = new WorkflowMemory();
