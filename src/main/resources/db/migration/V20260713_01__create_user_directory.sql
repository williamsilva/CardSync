-- Cache local (read-only) do id/nome/username de usuários do NimbusAuth. Sem FK: o id é
-- gerenciado por um sistema/banco separado (NimbusAuth). Ver UserDirectoryService.
CREATE TABLE cs_user_directory (
  id UUID NOT NULL,
  username VARCHAR(120) NOT NULL,
  name VARCHAR(120) NOT NULL,
  synced_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id)
);
