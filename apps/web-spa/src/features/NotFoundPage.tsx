import { Link } from 'react-router-dom';
import { Button, Card } from '@/components/ui';

export default function NotFoundPage() {
  return (
    <div className="grid min-h-[60vh] place-items-center px-4">
      <Card className="max-w-md p-8 text-center">
        <h1 className="text-xl font-bold">Página não encontrada</h1>
        <p className="mt-2 text-sm text-muted">O endereço não existe ou foi removido.</p>
        <Link to="/">
          <Button className="mt-5">Explorar eventos</Button>
        </Link>
      </Card>
    </div>
  );
}
