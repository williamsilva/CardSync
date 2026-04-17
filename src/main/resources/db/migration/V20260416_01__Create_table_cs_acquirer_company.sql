CREATE TABLE cs_acquirer_establishment (
  id BINARY(16) NOT NULL,
  acquirer_id BINARY(16) NOT NULL,
  establishment_id BINARY(16) NOT NULL,
  FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id),
  FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id),
   UNIQUE (establishment_id, acquirer_id)
)engine=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE cs_acquirer_company (
  id BINARY(16) NOT NULL,
  company_id BINARY(16) NOT NULL,
  acquirer_id BINARY(16) NOT NULL,
  FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id),
  FOREIGN KEY (company_id) REFERENCES cs_company(id),
   UNIQUE (acquirer_id, company_id)
)engine=InnoDB DEFAULT CHARSET=UTF8MB4;