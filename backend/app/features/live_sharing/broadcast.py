import asyncio


class ShareBroadcaster:
    """Registre en mémoire (process unique) : diffuse chaque mise à jour de score
    à tous les abonnés SSE d'un token donné."""

    def __init__(self) -> None:
        self._subscribers: dict[str, list[asyncio.Queue]] = {}

    def subscribe(self, token: str) -> asyncio.Queue:
        queue: asyncio.Queue = asyncio.Queue()
        self._subscribers.setdefault(token, []).append(queue)
        return queue

    def unsubscribe(self, token: str, queue: asyncio.Queue) -> None:
        queues = self._subscribers.get(token)
        if not queues:
            return
        if queue in queues:
            queues.remove(queue)
        if not queues:
            self._subscribers.pop(token, None)

    def publish(self, token: str, snapshot: dict) -> None:
        for queue in self._subscribers.get(token, []):
            queue.put_nowait(snapshot)


broadcaster = ShareBroadcaster()
