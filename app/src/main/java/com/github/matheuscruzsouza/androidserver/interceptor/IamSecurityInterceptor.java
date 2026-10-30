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

    public IamSecurityInterceptor() {
    }

    public IamSecurityInterceptor(IamService iamService) {
        this.iamService = iamService;
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
        if (path.startsWith("/web/fragments/") || path.startsWith("/web/upload") || path.startsWith("/web/create-folder") || path.startsWith("/web/delete") || path.startsWith("/web/files") || path.startsWith("/web/download") || path.startsWith("/web/view") || path.startsWith("/party")) {
            IamUser user = getIamService().authenticate(session);
            if (user == null) {
                return false;
            }
            session.getParms().put("_authenticated_iam_user", user.getAccessKey());
            return true;
        }

        // Rotas públicas liberadas (Web UI raiz '/', assets estáticos, health, etc.)
        if (!path.startsWith("/s3") && !path.startsWith("/api/iam")) {
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
        return getIamService().canPerform(user, s3Action);
    }

    private String resolveS3Action(NanoHTTPD.Method method, String path) {
        if (path.equals("/s3") || path.equals("/s3/")) {
            return "s3:ListAllMyBuckets";
        }

        String sub = path.substring(3);
        if (sub.startsWith("/")) sub = sub.substring(1);
        boolean isBucketOnly = !sub.contains("/");

        if (method == NanoHTTPD.Method.GET) {
            return isBucketOnly ? "s3:ListBucket" : "s3:GetObject";
        } else if (method == NanoHTTPD.Method.PUT || method == NanoHTTPD.Method.POST) {
            return "s3:PutObject";
        } else if (method == NanoHTTPD.Method.DELETE) {
            return "s3:DeleteObject";
        }

        return "s3:Unknown";
    }
}
