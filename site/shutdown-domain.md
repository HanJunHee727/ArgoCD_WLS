# Shutting down a domain

To shut down a domain, issue the following command:

```
kubectl delete domain DOMAINUID
```

Replace `DOMAINUID` with the UID of the target domain.

This command will remove the domain custom resource for the target domain.  The operator will be notified that the custom resource has been removed, and it will initiate the following actions:

*	Remove any Ingress associated with the domain.
*	Initiate a graceful shutdown of each server in the domain, Managed Servers first and then the Administration Server last.
*	Remove any services associated with the domain.

The operator will not delete any of the content on the persistent volume.  This command simply shuts down the domain; it does not remove it.
