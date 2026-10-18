export function SettingsPage() {
  return (
    <main className="panel">
      <h1>Configurações</h1>
      <section aria-label="Perfil">
        <h2>Perfil</h2>
        <p>Um responsável nesta versão. Nome e e-mail não são editados aqui.</p>
      </section>
      <section aria-label="Preferências">
        <h2>Preferências</h2>
        <p>America/Sao_Paulo</p>
        <p>A data não tem hora.</p>
      </section>
      <section aria-label="Segurança">
        <h2>Segurança</h2>
        <p>Não há recuperação de senha por e-mail.</p>
        <p>A troca de senha é o script local, nesta máquina.</p>
        <p>Convite de outro responsável fica para depois.</p>
      </section>
    </main>
  );
}
