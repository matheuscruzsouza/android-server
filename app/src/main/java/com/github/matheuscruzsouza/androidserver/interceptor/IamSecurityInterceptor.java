package com.github.matheuscruzsouza.androidserver.interceptor;

import com.github.matheuscruzsouza.androidserver.model.IamUser;
import com.github.matheuscruzsouza.androidserver.service.IamService;
import com.github.matheuscruzsouza.nanospring.annotation.Autowired;
import com.github.matheuscruzsouza.nanospring.annotation.Interceptor;
import com.github.matheuscruzsouza.nanospring.annotation.Order;
import com.github.matheuscruzsouza.nanospring.handler.HandlerInterceptor;

import fi.iki.elonen.NanoHTTPD;

@Interceptor
@Order(2)
public class IamSecurityInterceptor implements HandlerInterceptor {

    @Autowired
    private IamService iamService;

    @Autowired
    private com.github.matheuscruzsouza.androidserver.storage.StorageManager storageManager;

    private com.github.matheuscruzsouza.androidserver.s3.S3RequestDispatcher s3Dispatcher;

    public IamSecurityInterceptor() {
    }

    public IamSecurityInterceptor(IamService iamService) {
        this.iamService = iamService;
    }

    private com.github.matheuscruzsouza.androidserver.s3.S3RequestDispatcher getS3Dispatcher() {
        if (s3Dispatcher == null) {
            s3Dispatcher = new com.github.matheuscruzsouza.androidserver.s3.S3RequestDispatcher(storageManager, getIamService());
        }
        return s3Dispatcher;
    }

    private IamService getIamService() {
        if (iamService == null) {
            iamService = new IamService();
        }
        return iamService;
    }

    @Override
    public boolean preHandle(NanoHTTPD.IHTTPSession session, String path) {
        // Bloqueia endpoints de fragmentos e ações web para não autenticados
        if (path.startsWith("/web/fragments/") || path.startsWith("/web/upload") || path.startsWith("/web/create-folder") || path.startsWith("/web/delete") || path.startsWith("/web/files") || path.startsWith("/web/download") || path.startsWith("/web/view")) {
            IamUser user = getIamService().authenticate(session);
            if (user == null) {
                return false;
            }
            session.getParms().put("_authenticated_iam_user", user.getAccessKey());
            return true;
        }

        // Rotas administrativas do IAM
        boolean isIamAdmin = path.startsWith("/api/iam");
        // Verifica se é uma requisição S3 (tanto alias legado /s3 quanto S3 nativo)
        boolean isS3 = path.startsWith("/s3") || getS3Dispatcher().isS3Request(session);

        // Se não for rota protegida (IAM ou S3), libera
        if (!isS3 && !isIamAdmin) {
            return true;
        }

        IamUser user = getIamService().authenticate(session);
        if (user == null) {
            return false;
        }

        // Armazena usuário autenticado para consulta posterior se necessário
        session.getParms().put("_authenticated_iam_user", user.getAccessKey());

        // Se for rota de administração do IAM (/api/iam/*), somente Admin pode gerenciar
        if (path.startsWith("/api/iam")) {
            return "Admin".equalsIgnoreCase(user.getPolicy()) || "*".equals(user.getPolicy());
        }

        // Verificação de permissões do S3
        String s3Action = resolveS3Action(session.getMethod(), path);
        String bucket = extractBucketFromPath(path);
        String userDefaultBucket = storageManager != null ? storageManager.getUserDefaultBucket(user) : null;
        return getIamService().canPerformInBucket(user, s3Action, bucket, userDefaultBucket);
    }

    private String extractBucketFromPath(String path) {
        String clean = path;
        if (clean.startsWith("/s3")) {
            clean = clean.substring(3);
        }
        if (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        if (clean.isEmpty()) return null;
        int slash = clean.indexOf('/');
        return slash >= 0 ? clean.substring(0, slash) : clean;
    }

    private String resolveS3Action(NanoHTTPD.Method method, String path) {
        if (path.equals("/s3") || path.equals("/s3/") || path.equals("/")) {
            return "s3:ListAllMyBuckets";
        }

        String sub = path;
        if (sub.startsWith("/s3")) {
            sub = sub.substring(3);
        }
        if (sub.startsWith("/")) sub = sub.substring(1);
        boolean isBucketOnly = !sub.contains("/");

        if (method == NanoHTTPD.Method.GET || method == NanoHTTPD.Method.HEAD) {
            return isBucketOnly ? "s3:ListBucket" : "s3:GetObject";
        } else if (method == NanoHTTPD.Method.PUT || method == NanoHTTPD.Method.POST) {
            return isBucketOnly ? "s3:CreateBucket" : "s3:PutObject";
        } else if (method == NanoHTTPD.Method.DELETE) {
            return isBucketOnly ? "s3:DeleteBucket" : "s3:DeleteObject";
        }

        return "s3:Unknown";
    }
}
