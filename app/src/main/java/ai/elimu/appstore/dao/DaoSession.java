package ai.elimu.appstore.dao;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.AbstractDaoSession;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.identityscope.IdentityScopeType;
import org.greenrobot.greendao.internal.DaoConfig;

import java.util.Map;

import ai.elimu.appstore.model.Application;
import ai.elimu.appstore.model.ApplicationVersion;

// THIS CODE IS GENERATED BY greenDAO, DO NOT EDIT.

/**
 * {@inheritDoc}
 * 
 * @see org.greenrobot.greendao.AbstractDaoSession
 */
public class DaoSession extends AbstractDaoSession {

    private final DaoConfig applicationDaoConfig;
    private final DaoConfig applicationVersionDaoConfig;

    private final ApplicationDao applicationDao;
    private final ApplicationVersionDao applicationVersionDao;

    public DaoSession(Database db, IdentityScopeType type, Map<Class<? extends AbstractDao<?, ?>>, DaoConfig>
            daoConfigMap) {
        super(db);

        applicationDaoConfig = daoConfigMap.get(ApplicationDao.class).clone();
        applicationDaoConfig.initIdentityScope(type);

        applicationVersionDaoConfig = daoConfigMap.get(ApplicationVersionDao.class).clone();
        applicationVersionDaoConfig.initIdentityScope(type);

        applicationDao = new ApplicationDao(applicationDaoConfig, this);
        applicationVersionDao = new ApplicationVersionDao(applicationVersionDaoConfig, this);

        registerDao(Application.class, applicationDao);
        registerDao(ApplicationVersion.class, applicationVersionDao);
    }
    
    public void clear() {
        applicationDaoConfig.clearIdentityScope();
        applicationVersionDaoConfig.clearIdentityScope();
    }

    public ApplicationDao getApplicationDao() {
        return applicationDao;
    }

    public ApplicationVersionDao getApplicationVersionDao() {
        return applicationVersionDao;
    }

}
