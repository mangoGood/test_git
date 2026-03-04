const pool = require('../config/database');
const { v4: uuidv4 } = require('uuid');

class Workflow {
    // 创建新的工作流
    static async create(data) {
        const id = uuidv4();
        const { name, sourceConnection, targetConnection, migrationMode } = data;
        
        const [result] = await pool.execute(
            `INSERT INTO workflows (id, name, source_connection, target_connection, status, is_billing, migration_mode) 
             VALUES (?, ?, ?, ?, 'pending', 1, ?)`,
            [id, name, sourceConnection, targetConnection, migrationMode || 'full']
        );
        
        return { id, name, status: 'pending', migrationMode: migrationMode || 'full' };
    }
    
    // 获取所有工作流
    static async findAll(page = 1, pageSize = 10, userId = null) {
        const offset = (page - 1) * pageSize;
        
        console.log(`findAll called with page=${page}, pageSize=${pageSize}, offset=${offset}, userId=${userId}`);
        
        let sql = `SELECT * FROM workflows WHERE is_deleted = 0`;
        const params = [];
        
        if (userId) {
            sql += ` AND user_id = ?`;
            params.push(userId);
        }
        
        sql += ` ORDER BY created_at DESC LIMIT ${pageSize} OFFSET ${offset}`;
        console.log(`Executing SQL: ${sql}`);
        console.log(`Params: ${JSON.stringify(params)}`);
        
        const [workflows] = await pool.execute(sql, params);
        
        let countSql = `SELECT COUNT(*) as total FROM workflows WHERE is_deleted = 0`;
        const countParams = [];
        
        if (userId) {
            countSql += ` AND user_id = ?`;
            countParams.push(userId);
        }
        
        const [countResult] = await pool.execute(countSql, countParams);
        
        console.log(`Found ${workflows.length} workflows, total: ${countResult[0].total}`);
        console.log(`First workflow created_at: ${workflows[0]?.created_at}, Last workflow created_at: ${workflows[workflows.length-1]?.created_at}`);
        
        return {
            list: workflows,
            total: countResult[0].total,
            page,
            pageSize
        };
    }
    
    // 根据ID获取工作流
    static async findById(id) {
        const [rows] = await pool.execute(
            `SELECT * FROM workflows WHERE id = ?`,
            [id]
        );
        return rows[0] || null;
    }
    
    // 更新工作流状态
    static async updateStatus(id, status, progress = null, errorMessage = null) {
        let sql = `UPDATE workflows SET status = ?`;
        const params = [status];
        
        if (progress !== null) {
            sql += `, progress = ?`;
            params.push(progress);
        }
        
        if (errorMessage) {
            sql += `, error_message = ?`;
            params.push(errorMessage);
        }
        
        if (status === 'completed' || status === 'failed') {
            sql += `, completed_at = NOW(), is_billing = 0`;
        }
        
        sql += ` WHERE id = ?`;
        params.push(id);
        
        const [result] = await pool.execute(sql, params);
        return result.affectedRows > 0;
    }
    
    // 删除工作流
    static async delete(id) {
        const [result] = await pool.execute(
            `DELETE FROM workflows WHERE id = ?`,
            [id]
        );
        return result.affectedRows > 0;
    }
    
    // 添加日志
    static async addLog(workflowId, level, message) {
        const [result] = await pool.execute(
            `INSERT INTO workflow_logs (workflow_id, level, message) VALUES (?, ?, ?)`,
            [workflowId, level, message]
        );
        return result.insertId;
    }
    
    // 获取工作流日志
    static async getLogs(workflowId) {
        const [rows] = await pool.execute(
            `SELECT * FROM workflow_logs WHERE workflow_id = ? ORDER BY created_at DESC`,
            [workflowId]
        );
        return rows;
    }
}

module.exports = Workflow;
