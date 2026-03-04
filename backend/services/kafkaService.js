const { Kafka } = require('kafkajs');

class KafkaService {
    constructor() {
        this.kafka = new Kafka({
            clientId: 'sync-task-backend',
            brokers: ['localhost:9092']
        });
        
        this.producer = this.kafka.producer();
        this.isConnected = false;
    }
    
    async connect() {
        try {
            await this.producer.connect();
            this.isConnected = true;
            console.log('Kafka producer connected successfully');
        } catch (error) {
            console.error('Failed to connect to Kafka:', error);
            throw error;
        }
    }
    
    async disconnect() {
        try {
            await this.producer.disconnect();
            this.isConnected = false;
            console.log('Kafka producer disconnected');
        } catch (error) {
            console.error('Error disconnecting from Kafka:', error);
        }
    }
    
    async sendTaskCreatedMessage(taskId, sourceConnection, targetConnection, migrationMode) {
        if (!this.isConnected) {
            console.warn('Kafka producer is not connected, skipping message send');
            return;
        }
        
        try {
            const message = {
                taskId,
                source: this.parseConnectionString(sourceConnection),
                target: this.parseConnectionString(targetConnection),
                migrationMode
            };
            
            await this.producer.send({
                topic: 'sync-task-created',
                messages: [
                    {
                        key: taskId,
                        value: JSON.stringify(message)
                    }
                ]
            });
            
            console.log(`Task created message sent to Kafka for task: ${taskId}, mode: ${migrationMode}`);
        } catch (error) {
            console.error('Error sending task created message to Kafka:', error);
            throw error;
        }
    }
    
    parseConnectionString(connectionString) {
        const regex = /mysql:\/\/([^:]+):([^@]+)@([^:]+):(\d+)\/(.+)/;
        const match = connectionString.match(regex);
        
        if (match) {
            return {
                host: match[3],
                port: parseInt(match[4]),
                database: match[5],
                username: match[1],
                password: match[2]
            };
        }
        
        return null;
    }
}

module.exports = new KafkaService();