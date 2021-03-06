CREATE TABLE XML_DOC_1 (
    XML_DOC_ID_NBR DECIMAL(19) NOT NULL,
    STRUCTURE_ID_NBR DECIMAL(22) NOT NULL,
    CREATE_MINT_CD CHAR(1) NOT NULL,
    MSG_PAYLOAD_QTY DECIMAL(22) NOT NULL,
    MSG_PAYLOAD1_IMG BLOB(2000) NOT NULL,
    MSG_PAYLOAD2_IMG BLOB(2000),
    MSG_PAYLOAD_SIZE_NBR DECIMAL(22),
    MSG_PURGE_DT DATE,
    DELETED_FLG CHAR(1) NOT NULL,
    LAST_UPDATE_SYSTEM_NM VARCHAR(30),
    LAST_UPDATE_TMSTP TIMESTAMP NOT NULL,
    MSG_MAJOR_VERSION_NBR DECIMAL(22),
    MSG_MINOR_VERSION_NBR DECIMAL(22),
    OPT_LOCK_TOKEN_NBR DECIMAL(22) DEFAULT 1,
    PRESET_DICTIONARY_ID_NBR DECIMAL(22) DEFAULT 0 NOT NULL
)
PARTITION BY COLUMN (XML_DOC_ID_NBR, STRUCTURE_ID_NBR)
REDUNDANCY 1;
CREATE UNIQUE INDEX XML_DOC_1_UK1
    ON XML_DOC_1(XML_DOC_ID_NBR,STRUCTURE_ID_NBR);
