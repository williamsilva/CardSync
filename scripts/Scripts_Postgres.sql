/*
DROP DATABASE cardsync;
CREATE DATABASE cardsync;

Nota: no Postgres, "SCHEMA" não é sinônimo de "DATABASE" como no MySQL (schema é um
namespace dentro do banco, não o banco inteiro) — o equivalente de recriar o banco
inteiro é DROP DATABASE / CREATE DATABASE. Além disso, você não pode rodar DROP DATABASE
estando conectado nele: conecte primeiro no banco "postgres" (ou outro) antes de rodar.
*/

UPDATE cs_transaction_erp SET status_transaction = '5', status_transaction_reason = '17' WHERE nsu is null;

/* Vendas Duplicadas*/
UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '1', status_transaction_reason = '14', canceled_date = '2025-12-31',
	observations = 'Venda marcada como excluída por duplicidade. Motivo: DUPLICITY.' WHERE nsu = '750750927' and "authorization"= '641681';


/* Vendas estornadas*/
UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '7', status_transaction_reason = '6', canceled_date = '2026-02-22',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: REVERSED.' WHERE nsu = '503274746' and "authorization"= 'ZMPTMM';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '7', status_transaction_reason = '6', canceled_date = '2026-02-08',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: REVERSED.' WHERE nsu = '503156338' and "authorization"= '398091';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '7', status_transaction_reason = '6', canceled_date = '2026-02-06',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: REVERSED.' WHERE nsu = '504779798' and "authorization"= '555187';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '7', status_transaction_reason = '6', canceled_date = '2026-04-19',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: REVERSED.' WHERE nsu = '11409387' and "authorization"= '730042';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '7', status_transaction_reason = '6', canceled_date = '2026-04-16',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: REVERSED.' WHERE nsu = '509239572' and "authorization"= 'R62648';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '7', status_transaction_reason = '6', canceled_date = '2026-07-25',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: REVERSED.' WHERE nsu = '344579562' and "authorization"= '796464';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '7', status_transaction_reason = '6', canceled_date = '2024-10-15',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: REVERSED.' WHERE nsu = '344767518' and "authorization"= '845259';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '7', status_transaction_reason = '6', canceled_date = '2024-11-15',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: REVERSED.' WHERE nsu = '346045286' and "authorization"= '012950';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '7', status_transaction_reason = '6', canceled_date = '2025-12-28',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: REVERSED.' WHERE nsu = '336060254' and "authorization"= '987656';

/* Vendas Desfeitas */
UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2024-07-07',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '350793870' and "authorization"= '550225';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2024-07-18',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '353858900' and "authorization"= '042663';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2024-07-25',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '347161170' and "authorization"= '272456';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2024-07-28',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '348513078' and "authorization"= '881913';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2024-08-31',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '365359648' and "authorization"= '252484';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2024-09-01',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '348779750' and "authorization"= '541042';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2024-09-07',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '359890048' and "authorization"= '892178';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2024-10-19',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '355907598' and "authorization"= '322155';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2024-10-26',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '347407770' and "authorization"= '697152';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2024-10-27',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '345793486' and "authorization"= '062932';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2024-11-16',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '342598388' and "authorization"= '411527';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2024-11-16',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '338299944' and "authorization"= '056084';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2024-12-15',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '508307352' and "authorization"= '981202';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2024-12-26',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '504979864' and "authorization"= 'M28290';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2025-01-02',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '506386966' and "authorization"= '273905';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2025-01-03',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '338138282' and "authorization"= '091343';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2025-01-10',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '503406444' and "authorization"= '260260';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2025-02-09',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '508225348' and "authorization"= '331988';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2025-03-01',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '511402124' and "authorization"= '719163';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2025-03-06',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '506528772' and "authorization"= '915610';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2025-03-29',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '339581538' and "authorization"= '502084';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2025-04-12',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '507942478' and "authorization"= 'DlJAhP';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2025-05-03',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '339543762' and "authorization"= '633549';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2025-05-18',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '502061856' and "authorization"= '635833';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2025-07-26',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '505834656' and "authorization"= '134233';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2025-11-02',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '338020438' and "authorization"= 'R27879';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2025-11-29',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '506621924' and "authorization"= '029751';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2025-11-30',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '507880546' and "authorization"= '239331';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2025-11-30',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '341293562' and "authorization"= '311303';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2025-12-30',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '509072368' and "authorization"= '213088';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2026-01-07',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '336629030' and "authorization"= '01204I';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2026-04-03',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '504994636' and "authorization"= '092638';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', status_transaction_reason = '6', canceled_date = '2026-05-27',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE.' WHERE nsu = '625893101' and "authorization"= '471767';

/* Dados invalidos */
