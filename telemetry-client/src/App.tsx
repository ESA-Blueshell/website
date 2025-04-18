import './App.css';
import { useEffect, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import axios from "axios";

interface Redirect {
    id: string;
    telemetry: {
        url: string;
    };
    createdAt: string;
    deletedAt: string | null;
}

function App() {
    const [messages, setMessages] = useState<Redirect[]>([]);

    useEffect(() => {
        const client = new Client({
            brokerURL: 'ws://localhost:8080/telemetry/ws',
            connectHeaders: {},
            debug: (str) => console.log(str),
            reconnectDelay: 5000,
            webSocketFactory: () => new SockJS('http://localhost:8080/telemetry/ws'),
        });

        client.onConnect = () => {
            console.log('Connected to WebSocket');
            client.subscribe('/clicks/updates', (message) => {
                const parsedMessage = JSON.parse(message.body);
                console.log('Received message:', parsedMessage.id);
                setMessages(parsedMessage);
            });
        };

        client.activate();

        return () => {
            client.deactivate();
        };
    }, []);

    useEffect(() => {
        const fetchRedirects = async () => {
            try {
                const response = await axios.get('http://localhost:8080/telemetry/redirects');
                setMessages(response.data);
            } catch (error) {
                console.error('Error fetching redirects:', error);
            }
        };

        fetchRedirects();
    }, []);

    return (
        <div className="flex flex-col border-spacing-2 items-center justify-center min-h-screen bg-gray-100">
        <table className="table-auto border-collapse border border-gray-300 bg-white shadow-lg divide-y divide-white">
            <thead className="divide-x divide-white">
                <tr>
                    <th className="border border-gray-300 px-4 py-2 bg-gray-200 font-bold text-gray-700">ID</th>
                    <th className="border border-gray-300 px-4 py-2 bg-gray-200 font-bold text-gray-700">URL</th>
                    <th className="border border-gray-300 px-4 py-2 bg-gray-200 font-bold text-gray-700">Created At</th>
                    <th className="border border-gray-300 px-4 py-2 bg-gray-200 font-bold text-gray-700">Deleted At</th>
                </tr>
            </thead>
            <tbody className="divide-y divide-white">
                {messages.map((row) => (
                    <tr key={row.id} className="hover:bg-gray-100 divide-x divide-white">
                        <td className="border border-gray-300 px-4 py-2 text-center">{row.id}</td>
                        <td className="border border-gray-300 px-4 py-2 text-blue-600 underline">
                            <a href={row.telemetry.url} target="_blank" rel="noopener noreferrer">{row.telemetry.url}</a>
                        </td>
                        <td className="border border-gray-300 px-4 py-2 text-center">{row.createdAt}</td>
                        <td className="border border-gray-300 px-4 py-2 text-center">{row.deletedAt}</td>
                    </tr>
                ))}
            </tbody>
        </table>
    </div>
    );
}

export default App;